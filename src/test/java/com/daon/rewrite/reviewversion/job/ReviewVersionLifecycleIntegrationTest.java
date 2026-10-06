package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionInput;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.client.ReviewClient;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.client.ReviewResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import com.daon.rewrite.reviewversion.dto.ReviewVersionListResponse;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.ReviewVersionQueryService;
import com.daon.rewrite.reviewversion.service.ReviewVersionCompletionService;
import com.daon.rewrite.reviewversion.service.SaveFinalAnswerInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ReviewVersionLifecycleIntegrationTest {

    @Autowired private CoverLetterService coverLetterService;
    @Autowired private CoverLetterDetailQueryService detailQueryService;
    @Autowired private ReviewVersionCommandService versionCommandService;
    @Autowired private ReviewVersionQueryService versionQueryService;
    @Autowired private ReviewVersionCompletionService completionService;
    @Autowired private ReviewVersionRepository versionRepository;
    @Autowired private ReviewVersionQuestionResultRepository resultRepository;
    @Autowired private CoverLetterRepository coverLetterRepository;
    @Autowired private CoverLetterQuestionRepository questionRepository;
    @Autowired private FirstReviewJobWorker firstReviewJobWorker;
    @Autowired private ReReviewJobWorker reReviewJobWorker;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ReviewJobEventListener eventListener;
    @MockitoBean private ReviewClient reviewClient;

    @Test
    void failedFirstReviewAndRetryEachKeepTheirOwnVersion() {
        CoverLetter coverLetter = createCoverLetter(2);
        List<String> questionIds = questionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId())
                .stream().map(question -> question.getId()).toList();

        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        String firstVersionId = versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId();
        assertThat(versionRepository.findById(firstVersionId).orElseThrow().getVersion()).isEqualTo("v0.1");
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).reviewVersion().value().getId())
                .isEqualTo(firstVersionId);
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).questions()).hasSize(2)
                .allMatch(question -> question.originalAnswer() != null && question.aiReport() == null);
        assertThat(detailQueryService.findVersion(coverLetter.getId(), firstVersionId).questions()).hasSize(2);
        assertThat(coverLetterService.submit(coverLetter.getId()).job().getId()).isEqualTo(first.job().getId());
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(1);

        when(reviewClient.reviewQuestion(any(), anyString())).thenAnswer(invocation -> {
            String questionId = invocation.getArgument(1);
            if (questionId.equals(questionIds.get(1))) {
                throw ReviewClientException.providerError(new IllegalStateException("test failure"));
            }
            return new ReviewResult(questionId, "첨삭 리포트", "수정된 답변");
        });
        firstReviewJobWorker.execute(first.job().getId());

        var failed = detailQueryService.findVersion(coverLetter.getId(), firstVersionId);
        assertThat(failed.reviewVersion().value().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        assertThat(failed.reviewJob().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        assertThat(failed.questions()).hasSize(2);
        assertThat(failed.questions().get(0).aiReport()).isEqualTo("첨삭 리포트");
        assertThat(failed.questions().get(1).aiReport()).isNull();
        assertThat(failed.questions()).allMatch(question -> question.questionResultId() == null);
        assertThat(CoverLetterDetailResponse.from(failed).reviewVersion().status())
                .isEqualTo(LlmJobStatus.FAILED);
        assertThat(coverLetterRepository.findById(coverLetter.getId()).orElseThrow().getLatestReviewedVersionId())
                .isNull();

        SubmitCoverLetterResult retry = coverLetterService.submit(coverLetter.getId());
        var versions = versionQueryService.findMyReviewVersions(coverLetter.getId());
        assertThat(versions).extracting(summary -> summary.reviewVersion().getVersion())
                .containsExactly("v0.1", "v0.2");
        assertThat(versions.get(0).isLatest()).isFalse();
        assertThat(versions.get(1).isLatest()).isTrue();
        assertThat(versions.get(1).reviewVersion().getStatus()).isEqualTo(LlmJobStatus.PENDING);

        when(reviewClient.reviewQuestion(any(), anyString())).thenAnswer(invocation ->
                new ReviewResult(invocation.getArgument(1), "새 리포트", "새 수정본"));
        firstReviewJobWorker.execute(retry.job().getId());

        assertThat(versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getStatus())
                .isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(
                versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getId())).hasSize(2);
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(2);
    }

    @Test
    void failedReReviewKeepsPreviousSuccessAndUsesNextNumberOnRetry() {
        CoverLetter coverLetter = createCoverLetter(1);
        when(reviewClient.reviewQuestion(any(), anyString())).thenAnswer(invocation ->
                new ReviewResult(invocation.getArgument(1), "첫 리포트", "첫 수정본"));

        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        firstReviewJobWorker.execute(first.job().getId());
        String successfulVersionId = versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId();
        String resultId = resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(successfulVersionId)
                .getFirst().getId();

        var reReview = versionCommandService.requestMyReReview(coverLetter.getId(), "다시 봐주세요");
        String failedVersionId = versionRepository.findByLlmJobId(reReview.job().getId()).orElseThrow().getId();
        assertThat(versionRepository.findById(failedVersionId).orElseThrow().getVersion()).isEqualTo("v0.2");
        assertThat(reReview.job().getRequestRefId()).isEqualTo(successfulVersionId);
        assertThat(versionCommandService.requestMyReReview(coverLetter.getId(), "다른 요구사항").job().getId())
                .isEqualTo(reReview.job().getId());
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(2);

        when(reviewClient.reviewQuestion(any(), anyString())).thenThrow(
                ReviewClientException.providerError(new IllegalStateException("test failure")));
        reReviewJobWorker.execute(reReview.job().getId());

        var versions = versionQueryService.findMyReviewVersions(coverLetter.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).isLatestReviewed()).isTrue();
        assertThat(versions.get(0).isLatest()).isFalse();
        assertThat(versions.get(1).isLatest()).isTrue();
        assertThat(versions.get(1).isLatestReviewed()).isFalse();
        assertThat(versions.get(1).reviewVersion().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        var response = ReviewVersionListResponse.from(versions);
        assertThat(response.items().get(1).status()).isEqualTo(LlmJobStatus.FAILED);
        assertThat(response.items().get(1).isLatest()).isTrue();
        assertThat(response.items().get(0).isLatestReviewed()).isTrue();
        assertThat(detailQueryService.findVersion(coverLetter.getId(), failedVersionId).reviewJob().getStatus())
                .isEqualTo(LlmJobStatus.FAILED);
        assertThat(coverLetterRepository.findById(coverLetter.getId()).orElseThrow().getLatestReviewedVersionId())
                .isEqualTo(successfulVersionId);
        assertThatThrownBy(() -> versionCommandService.saveMyFinalAnswers(
                coverLetter.getId(), failedVersionId, List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REVIEW_VERSION_NOT_LATEST));
        versionCommandService.saveMyFinalAnswers(coverLetter.getId(), successfulVersionId,
                List.of(new SaveFinalAnswerInput(resultId, "보존할 최종 작성본")));

        var retry = versionCommandService.requestMyReReview(coverLetter.getId(), null);
        assertThat(versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getVersion())
                .isEqualTo("v0.3");
        assertThat(retry.job().getRequestRefId()).isEqualTo(successfulVersionId);
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(3);

        doAnswer(invocation -> new ReviewResult(invocation.getArgument(1), "재첨삭 리포트", "재첨삭 수정본"))
                .when(reviewClient).reviewQuestion(any(), anyString());
        String retryVersionId = versionRepository.findByLlmJobId(retry.job().getId()).orElseThrow().getId();
        reReviewJobWorker.execute(retry.job().getId());

        assertThat(versionRepository.findById(retryVersionId).orElseThrow().getStatus())
                .isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(coverLetterRepository.findById(coverLetter.getId()).orElseThrow().getLatestReviewedVersionId())
                .isEqualTo(retryVersionId);
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(retryVersionId))
                .hasSize(1)
                .allMatch(result -> result.getOriginalAnswer().equals("보존할 최종 작성본"));
        assertThat(completionService.completeReReview(retry.job().getId()).reviewVersion().getId())
                .isEqualTo(retryVersionId);
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(3);
        assertThat(resultRepository.findByReviewVersionIdOrderByQuestionOrderAsc(retryVersionId)).hasSize(1);
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).reviewJob()).isNull();
        assertThat(detailQueryService.findVersion(coverLetter.getId(), failedVersionId)
                .reviewVersion().value().getStatus()).isEqualTo(LlmJobStatus.FAILED);
        assertThatThrownBy(() -> versionCommandService.saveMyFinalAnswers(
                coverLetter.getId(), successfulVersionId,
                List.of(new SaveFinalAnswerInput(resultId, "과거 버전 변경"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REVIEW_VERSION_NOT_LATEST));
    }

    @Test
    void legacySuccessfulVersionWithoutJobRemainsReadableAndEditable() {
        CoverLetter coverLetter = createCoverLetter(1);
        when(reviewClient.reviewQuestion(any(), anyString())).thenAnswer(invocation ->
                new ReviewResult(invocation.getArgument(1), "기존 리포트", "기존 수정본"));
        SubmitCoverLetterResult first = coverLetterService.submit(coverLetter.getId());
        firstReviewJobWorker.execute(first.job().getId());
        String versionId = versionRepository.findByLlmJobId(first.job().getId()).orElseThrow().getId();
        jdbcTemplate.update("update review_versions set llm_job_id = null where id = ?", versionId);

        var versions = versionQueryService.findMyReviewVersions(coverLetter.getId());
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().reviewVersion().getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
        assertThat(versions.getFirst().isLatest()).isTrue();
        assertThat(versions.getFirst().isLatestReviewed()).isTrue();
        var detail = detailQueryService.findVersion(coverLetter.getId(), versionId);
        assertThat(detail.reviewJob()).isNull();
        assertThat(detail.questions()).hasSize(1);
        versionCommandService.saveMyFinalAnswers(coverLetter.getId(), versionId,
                List.of(new SaveFinalAnswerInput(detail.questions().getFirst().questionResultId(), "기존 최종 작성본")));
        assertThat(detailQueryService.findCurrent(coverLetter.getId()).questions().getFirst().finalAnswer())
                .isEqualTo("기존 최종 작성본");

        var reReview = versionCommandService.requestMyReReview(coverLetter.getId(), null);
        assertThat(versionRepository.findByLlmJobId(reReview.job().getId()).orElseThrow().getVersion())
                .isEqualTo("v0.2");
        assertThat(reReview.job().getRequestRefId()).isEqualTo(versionId);
    }

    @Test
    void invalidSubmitAndCanceledJobDoNotLeaveAnIncorrectVersion() {
        CoverLetter invalid = coverLetterService.create();
        assertThatThrownBy(() -> coverLetterService.submit(invalid.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(versionRepository.countByCoverLetterId(invalid.getId())).isZero();

        CoverLetter coverLetter = createCoverLetter(1);
        SubmitCoverLetterResult submitted = coverLetterService.submit(coverLetter.getId());
        String versionId = versionRepository.findByLlmJobId(submitted.job().getId()).orElseThrow().getId();
        coverLetterService.deleteMyCoverLetter(coverLetter.getId());

        assertThat(versionRepository.findById(versionId).orElseThrow().getStatus())
                .isEqualTo(LlmJobStatus.CANCELED);
        assertThatThrownBy(() -> detailQueryService.findVersion(coverLetter.getId(), versionId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void concurrentSubmitsCreateOneJobAndOneVersion() throws Exception {
        CoverLetter coverLetter = createCoverLetter(1);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return coverLetterService.submit(coverLetter.getId());
            });
            var second = executor.submit(() -> {
                start.await();
                return coverLetterService.submit(coverLetter.getId());
            });
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).job().getId())
                    .isEqualTo(second.get(5, TimeUnit.SECONDS).job().getId());
        }
        assertThat(versionRepository.countByCoverLetterId(coverLetter.getId())).isEqualTo(1);
    }

    private CoverLetter createCoverLetter(int questionCount) {
        CoverLetter coverLetter = coverLetterService.create();
        coverLetterService.saveBasicInfo(coverLetter.getId(), "백엔드 지원", "회사", "개발자", null);
        coverLetterService.savePreferences(coverLetter.getId(), "Spring 개발 경험");
        List<SaveQuestionInput> questions = java.util.stream.IntStream.range(0, questionCount)
                .mapToObj(index -> new SaveQuestionInput(
                        "문항 " + (index + 1), 1000, "충분한 길이의 원본 답변입니다."))
                .toList();
        coverLetterService.saveQuestions(coverLetter.getId(), questions);
        return coverLetter;
    }
}
