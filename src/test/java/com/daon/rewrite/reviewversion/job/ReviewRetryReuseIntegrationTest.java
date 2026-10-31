package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.client.ReviewClient;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.client.ReviewResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** 실패 후 수동 재시도에서 성공 문항을 보존하고 실패 문항만 LLM에 다시 요청하는지 실제 Worker와 DB로 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
class ReviewRetryReuseIntegrationTest {

    @Autowired private CoverLetterService coverLetterService;
    @Autowired private CoverLetterDetailQueryService detailQueryService;
    @Autowired private ReviewVersionCommandService versionCommandService;
    @Autowired private ReviewVersionRepository versionRepository;
    @Autowired private ReviewJobQuestionResultRepository jobQuestionResultRepository;
    @Autowired private ReviewVersionQuestionResultRepository resultRepository;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private CoverLetterQuestionRepository questionRepository;
    @Autowired private FirstReviewJobWorker firstReviewJobWorker;
    @Autowired private ReReviewJobWorker reReviewJobWorker;

    @MockitoBean private ReviewJobEventListener eventListener;
    @MockitoBean private ReviewClient reviewClient;

    /** 호출된 문항 ID를 기록하고 failing 문항만 실패시킨다. 성공 결과는 호출 횟수를 포함해 매번 다르게 만든다. */
    private List<String> stubClient(Set<String> failingQuestionIds, String label) {
        List<String> calls = new CopyOnWriteArrayList<>();
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            String questionId = invocation.getArgument(1);
            calls.add(questionId);
            if (failingQuestionIds.contains(questionId)) {
                throw ReviewClientException.providerError(new IllegalStateException("test failure"));
            }
            int count = counts.merge(questionId, 1, Integer::sum);
            return new ReviewResult(questionId, label + " 리포트 " + questionId + "#" + count,
                    label + " 수정본 " + questionId + "#" + count);
        }).when(reviewClient).reviewQuestion(any(), anyString());
        return calls;
    }

    @Test
    void firstReviewRetryCallsOnlyFailedQuestionAndKeepsSuccessfulResults() {
        CoverLetter coverLetter = createCoverLetter(5);
        List<String> questionIds = questionIds(coverLetter);

        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        List<String> firstCalls = stubClient(Set.of(questionIds.get(4)), "첫");
        firstReviewJobWorker.execute(first.job().getId());
        // 실패 문항은 문항 단위 재시도로 두 번 호출되고 나머지는 한 번씩 호출된다.
        assertThat(firstCalls).hasSize(6);
        var failed = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(failed.reviewJob().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        List<String> firstReports = failed.questions().stream().map(question -> question.aiReport()).toList();
        assertThat(firstReports.subList(0, 4)).doesNotContainNull();
        assertThat(firstReports.get(4)).isNull();

        SubmitCoverLetterResult retry = coverLetterService.submit(coverLetter.getId());
        assertThat(retry.job().getId()).isNotEqualTo(first.job().getId());
        // Worker 시작 전 상세 조회에서도 재사용 문항과 진행률이 복구된다.
        var pending = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(pending.reviewJob().getProgressCurrent()).isEqualTo(4);
        assertThat(pending.questions().stream().map(question -> question.aiReport()).toList().subList(0, 4))
                .containsExactlyElementsOf(firstReports.subList(0, 4));
        assertThat(pending.questions().get(4).aiReport()).isNull();
        List<String> retryCalls = stubClient(Set.of(), "재시도");
        firstReviewJobWorker.execute(retry.job().getId());

        assertThat(retryCalls).containsExactly(questionIds.get(4));
        var completed = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(completed.reviewJob()).isNull();
        var versionId = versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow();
        assertThat(versionId.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(coverLetterRepository.findById(coverLetter.getId()).orElseThrow().getLatestReviewedVersionId())
                .isEqualTo(versionId.getId());
        List<ReviewVersionQuestionResult> results =
                resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(versionId.getId());
        assertThat(results).hasSize(5);
        assertThat(results.subList(0, 4)).extracting(ReviewVersionQuestionResult::getAiReport)
                .containsExactlyElementsOf(firstReports.subList(0, 4));
        assertThat(results.get(4).getAiReport()).contains("재시도");
        // 실패 시도의 버전과 문항 결과는 그대로 남는다.
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(2);
        assertThat(detailQueryService.findVersion(coverLetter.getId(),
                versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId())
                .questions().get(0).aiReport()).isEqualTo(firstReports.get(0));
    }

    @Test
    void repeatedFailureAccumulatesSuccessfulQuestionsAcrossRetries() {
        CoverLetter coverLetter = createCoverLetter(3);
        List<String> questionIds = questionIds(coverLetter);

        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        stubClient(Set.of(questionIds.get(1), questionIds.get(2)), "첫");
        firstReviewJobWorker.execute(first.job().getId());

        SubmitCoverLetterResult second = coverLetterService.submit(coverLetter.getId());
        List<String> secondCalls = stubClient(Set.of(questionIds.get(2)), "둘째");
        firstReviewJobWorker.execute(second.job().getId());
        assertThat(secondCalls).containsExactlyInAnyOrder(
                questionIds.get(1), questionIds.get(2), questionIds.get(2));
        var afterSecond = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(afterSecond.reviewJob().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        assertThat(afterSecond.reviewJob().getProgressCurrent()).isEqualTo(2);
        assertThat(afterSecond.questions().stream().map(question -> question.aiReport()).toList())
                .satisfies(reports -> {
                    assertThat(reports.get(0)).contains("첫");
                    assertThat(reports.get(1)).contains("둘째");
                    assertThat(reports.get(2)).isNull();
                });

        SubmitCoverLetterResult third = coverLetterService.submit(coverLetter.getId());
        List<String> thirdCalls = stubClient(Set.of(), "셋째");
        firstReviewJobWorker.execute(third.job().getId());
        assertThat(thirdCalls).containsExactly(questionIds.get(2));
        var versionId = versionRepository.findByLlmJobId(third.job().getId()).orElseThrow().getId();
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(versionId))
                .extracting(ReviewVersionQuestionResult::getAiReport)
                .satisfiesExactly(
                        report -> assertThat(report).contains("첫"),
                        report -> assertThat(report).contains("둘째"),
                        report -> assertThat(report).contains("셋째"));
    }

    /** 3문항 기준 성공 → 최종 작성본 저장 → 재첨삭(셋째 문항만 실패)까지 만들고 실패한 재첨삭 정보를 돌려준다. */
    private FailedReReview failedReReviewWithOneFailure(CoverLetter coverLetter) {
        List<String> questionIds = questionIds(coverLetter);
        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        stubClient(Set.of(), "기준");
        firstReviewJobWorker.execute(first.job().getId());
        String baseVersionId = versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId();
        List<ReviewVersionQuestionResult> baseResults =
                resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(baseVersionId);
        saveFinalAnswers(coverLetter, baseVersionId, baseResults, List.of("기준 최종본 0", "기준 최종본 1", "기준 최종본 2"));

        var reReview = versionCommandService.requestMyReReview(coverLetter.getId(), "첫 요구사항");
        stubClient(Set.of(questionIds.get(2)), "재첨삭");
        reReviewJobWorker.execute(reReview.job().getId());
        var failed = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(failed.reviewJob().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        return new FailedReReview(
                baseVersionId,
                baseResults,
                failed.questions().stream().map(question -> question.aiReport()).toList()
        );
    }

    private record FailedReReview(
            String baseVersionId,
            List<ReviewVersionQuestionResult> baseResults,
            List<String> reports
    ) {
    }

    private void saveFinalAnswers(
            CoverLetter coverLetter,
            String versionId,
            List<ReviewVersionQuestionResult> results,
            List<String> answers
    ) {
        versionCommandService.saveMyFinalAnswers(coverLetter.getId(), versionId, IntStream.range(0, answers.size())
                .mapToObj(index -> new SaveFinalAnswerInput(results.get(index).getId(), answers.get(index)))
                .toList());
    }

    @Test
    void reReviewRetryWithoutChangesInheritsInstructionAndRecoversProgressBeforeWorkerStarts() {
        CoverLetter coverLetter = createCoverLetter(3);
        List<String> questionIds = questionIds(coverLetter);
        FailedReReview failed = failedReReviewWithOneFailure(coverLetter);
        int versionsBefore = (int) versionRepository.countByCoverLetterId(coverLetter.getId());

        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), null);
        assertThat(retry.job().getRequestInstruction()).isEqualTo("첫 요구사항");
        assertThat(retry.job().getRequestRefId()).isEqualTo(failed.baseVersionId());
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(versionsBefore + 1);

        // 진행 중 중복 요청은 같은 Job을 반환하고 새 버전을 만들지 않는다.
        assertThat(versionCommandService.requestMyReReview(coverLetter.getId(), "무시되는 요구사항").job().getId())
                .isEqualTo(retry.job().getId());
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(versionsBefore + 1);

        // Worker 시작 전 상세 조회와 SSE 스냅샷이 읽는 완료 문항 조회에서 재사용 문항과 진행률이 복구된다.
        var pending = detailQueryService.findCurrent(coverLetter.getId());
        assertThat(pending.reviewJob().getProgressCurrent()).isEqualTo(2);
        assertThat(pending.reviewJob().getProgressTotal()).isEqualTo(3);
        assertThat(pending.questions().stream().map(question -> question.aiReport()).toList().subList(0, 2))
                .containsExactlyElementsOf(failed.reports().subList(0, 2));
        assertThat(jobQuestionResultRepository.findByLlmJobIdAndStatusOrderByQuestionOrderAsc(
                retry.job().getId(), ReviewJobQuestionResultStatus.COMPLETED))
                .extracting(result -> result.getQuestion().getId())
                .containsExactly(questionIds.get(0), questionIds.get(1));

        List<String> retryCalls = stubClient(Set.of(), "재시도");
        reReviewJobWorker.execute(retry.job().getId());
        assertThat(retryCalls).containsExactly(questionIds.get(2));

        String retryVersionId = versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getId();
        assertThat(coverLetterRepository.findById(coverLetter.getId()).orElseThrow().getLatestReviewedVersionId())
                .isEqualTo(retryVersionId);
        List<ReviewVersionQuestionResult> results =
                resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(retryVersionId);
        assertThat(results).extracting(ReviewVersionQuestionResult::getOriginalAnswer)
                .containsExactly("기준 최종본 0", "기준 최종본 1", "기준 최종본 2");
        assertThat(results.subList(0, 2)).extracting(ReviewVersionQuestionResult::getAiReport)
                .containsExactlyElementsOf(failed.reports().subList(0, 2));
        assertThat(results.get(2).getAiReport()).contains("재시도");
    }

    @Test
    void reReviewRetryRerunsOnlyQuestionsWhoseFinalAnswerChanged() {
        CoverLetter coverLetter = createCoverLetter(3);
        List<String> questionIds = questionIds(coverLetter);
        FailedReReview failed = failedReReviewWithOneFailure(coverLetter);
        saveFinalAnswers(coverLetter, failed.baseVersionId(), failed.baseResults(),
                List.of("기준 최종본 0", "수정한 최종본 1", "기준 최종본 2"));

        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), null);
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).reviewJob().getProgressCurrent())
                .isEqualTo(1);
        List<String> retryCalls = stubClient(Set.of(), "재시도");
        reReviewJobWorker.execute(retry.job().getId());

        assertThat(retryCalls).containsExactlyInAnyOrder(questionIds.get(1), questionIds.get(2));
        String retryVersionId = versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getId();
        List<ReviewVersionQuestionResult> results =
                resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(retryVersionId);
        assertThat(results).extracting(ReviewVersionQuestionResult::getOriginalAnswer)
                .containsExactly("기준 최종본 0", "수정한 최종본 1", "기준 최종본 2");
        assertThat(results.get(0).getAiReport()).isEqualTo(failed.reports().get(0));
        assertThat(results.get(1).getAiReport()).contains("재시도");
    }

    @Test
    void reReviewRetryWithDifferentInstructionRerunsEveryQuestion() {
        CoverLetter coverLetter = createCoverLetter(3);
        failedReReviewWithOneFailure(coverLetter);

        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), "새 요구사항");
        assertThat(retry.job().getRequestInstruction()).isEqualTo("새 요구사항");
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).reviewJob().getProgressCurrent()).isZero();
        List<String> retryCalls = stubClient(Set.of(), "재시도");
        reReviewJobWorker.execute(retry.job().getId());

        assertThat(retryCalls).containsExactlyInAnyOrderElementsOf(questionIds(coverLetter));
        String retryVersionId = versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getId();
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(retryVersionId))
                .extracting(ReviewVersionQuestionResult::getAiReport)
                .allMatch(report -> report.contains("재시도"));
    }

    @Test
    void canceledRetryJobKeepsReusedResultsAndCallsNothing() {
        CoverLetter coverLetter = createCoverLetter(3);
        failedReReviewWithOneFailure(coverLetter);
        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), null);

        coverLetterService.deleteMyCoverLetter(coverLetter.getId());
        List<String> calls = stubClient(Set.of(), "취소 후");
        reReviewJobWorker.execute(retry.job().getId());

        assertThat(calls).isEmpty();
        assertThat(versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getStatus())
                .isEqualTo(LlmJobStatus.CANCELED);
        assertThat(jobQuestionResultRepository.findByLlmJobIdAndStatusOrderByQuestionOrderAsc(
                retry.job().getId(), ReviewJobQuestionResultStatus.COMPLETED)).hasSize(2);
    }

    @Test
    void newReReviewAfterFullSuccessCallsEveryQuestion() {
        CoverLetter coverLetter = createCoverLetter(3);
        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        stubClient(Set.of(), "기준");
        firstReviewJobWorker.execute(first.job().getId());

        var reReview = versionCommandService.requestMyReReview(coverLetter.getId(), null);
        List<String> calls = stubClient(Set.of(), "재첨삭");
        reReviewJobWorker.execute(reReview.job().getId());

        assertThat(calls).containsExactlyInAnyOrderElementsOf(questionIds(coverLetter));
        assertThat(reReview.job().getRequestInstruction()).isNull();
    }

    @Test
    void retryAfterTotalFailureStartsFreshWithEditedFinalAnswers() {
        CoverLetter coverLetter = createCoverLetter(1);
        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        stubClient(Set.of(), "기준");
        firstReviewJobWorker.execute(first.job().getId());
        String baseVersionId = versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId();
        String resultId = resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(baseVersionId)
                .getFirst().getId();

        var failedReReview = versionCommandService.requestMyReReview(coverLetter.getId(), "이전 요구사항");
        stubClient(Set.copyOf(questionIds(coverLetter)), "실패");
        reReviewJobWorker.execute(failedReReview.job().getId());

        // 성공 문항이 없으면 재사용할 결과가 없으므로 수정한 최종 작성본과 새 요구사항으로 새로 시작한다.
        versionCommandService.saveMyFinalAnswers(coverLetter.getId(), baseVersionId,
                List.of(new SaveFinalAnswerInput(resultId, "수정한 최종본")));
        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), "새 요구사항");
        assertThat(retry.job().getRequestInstruction()).isEqualTo("새 요구사항");
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).questions().getFirst().originalAnswer())
                .isEqualTo("수정한 최종본");
    }

    private List<String> questionIds(CoverLetter coverLetter) {
        return questionRepository.findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId())
                .stream().map(question -> question.getId()).toList();
    }

    private CoverLetter createCoverLetter(int questionCount) {
        CoverLetter coverLetter = coverLetterService.create();
        coverLetterService.saveBasicInfo(coverLetter.getId(), "백엔드 지원", "회사", "개발자", null);
        coverLetterService.savePreferences(coverLetter.getId(), "Spring 개발 경험");
        List<SaveQuestionInput> questions = IntStream.range(0, questionCount)
                .mapToObj(index -> new SaveQuestionInput(
                        "문항 " + (index + 1), 1000, "충분한 길이의 원본 답변입니다."))
                .toList();
        coverLetterService.saveQuestions(coverLetter.getId(), questions);
        return coverLetter;
    }
}
