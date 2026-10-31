package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobRequestRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;
import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResultStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.repository.ReviewJobQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 새 첨삭 Job의 문항별 입력 스냅샷을 만들면서, 실패한 직전 시도의 성공 문항 결과를 이어받는다.
 * 최초 첨삭과 재첨삭이 같은 규칙을 쓰며 호출자의 트랜잭션 안에서 사용한다.
 * 이전 Job·버전·문항 결과는 변경하지 않고 새 Job의 임시 결과로만 복사한다.
 */
@Service
@RequiredArgsConstructor
public class ReviewRetrySnapshotService {

    private static final String JOB_QUESTION_RESULT_ID_PREFIX = "rjqr";

    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewJobQuestionResultRepository jobQuestionResultRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    /** 새 Job에서 LLM이 입력으로 사용할 문항과 답변이다. 최초 첨삭은 원본, 재첨삭은 최신 최종 작성본이다. */
    public record QuestionInput(CoverLetterQuestion question, String answer) {
    }

    /**
     * 새 Job에 적용할 요구사항과 재사용할 문항 결과다.
     * requestInstruction은 직전 시도에서 이어받았을 수 있으므로 Job 생성에는 이 값을 사용한다.
     */
    public record RetryPlan(String requestInstruction, Map<String, ReviewJobQuestionResult> reusableByQuestionId) {
    }

    /**
     * 직전 시도가 같은 종류의 FAILED이면 현재 입력과 같은 조건으로 성공한 문항을 재사용 대상으로 고른다.
     * 재첨삭은 같은 기준 성공 버전의 시도만 대상이다.
     * 요구사항은 비어 있으면 직전 값을 이어받고, 직전과 다른 값이면 그 결과가 새 요구사항으로 생성된 것이 아니므로 재사용하지 않는다.
     * 문항은 질문·순서·글자 수 제한·입력 답변이 모두 같을 때만 재사용한다.
     *
     * @param versionNumber 새 시도의 버전 번호이며 이보다 작은 번호의 바로 앞 시도를 직전 시도로 본다.
     * @param requestedInstruction 정규화된 새 요구사항이며 없으면 null이다. 최초 첨삭은 항상 null이다.
     */
    public RetryPlan plan(
            String coverLetterId,
            long versionNumber,
            LlmJobType type,
            String sourceReviewVersionId,
            String requestedInstruction,
            List<QuestionInput> inputs
    ) {
        RetryPlan fresh = new RetryPlan(requestedInstruction, Map.of());
        LlmJob previousJob = reviewVersionRepository
                .findFirstByCoverLetterIdAndVersionNumberLessThanOrderByVersionNumberDesc(coverLetterId, versionNumber)
                .map(ReviewVersion::getLlmJob)
                .orElse(null);
        if (previousJob == null
                || previousJob.getStatus() != LlmJobStatus.FAILED
                || previousJob.getType() != type
                || !isSameSource(previousJob, sourceReviewVersionId)) {
            return fresh;
        }

        String instruction = requestedInstruction == null ? previousJob.getRequestInstruction() : requestedInstruction;
        if (!Objects.equals(instruction, previousJob.getRequestInstruction())) {
            return fresh;
        }

        Map<String, QuestionInput> inputsByQuestionId = inputs.stream()
                .collect(Collectors.toMap(input -> input.question().getId(), Function.identity()));
        Map<String, ReviewJobQuestionResult> reusable = jobQuestionResultRepository
                .findByLlmJobIdOrderByQuestionOrderAsc(previousJob.getId()).stream()
                .filter(result -> isReusable(result, inputsByQuestionId.get(result.getQuestion().getId())))
                .collect(Collectors.toMap(result -> result.getQuestion().getId(), Function.identity()));
        // 이어받을 결과가 없으면 요구사항도 이어받지 않고 요청 값을 그대로 사용한다.
        return reusable.isEmpty() ? fresh : new RetryPlan(instruction, reusable);
    }

    /**
     * 새 Job의 문항 결과를 입력 순서대로 저장한다. 재사용 대상은 결과까지 복사하고 나머지는 처리 대기 상태로 둔다.
     * 재사용한 문항 수를 Job 진행률에 반영한다.
     */
    public List<ReviewJobQuestionResult> stage(LlmJob job, RetryPlan plan, List<QuestionInput> inputs) {
        Instant now = Instant.now(clock);
        List<ReviewJobQuestionResult> staged = jobQuestionResultRepository.saveAll(inputs.stream()
                .map(input -> {
                    ReviewJobQuestionResult source = plan.reusableByQuestionId().get(input.question().getId());
                    String id = idGenerator.generate(JOB_QUESTION_RESULT_ID_PREFIX);
                    return source == null
                            ? ReviewJobQuestionResult.processing(id, job, input.question(), input.answer())
                            : ReviewJobQuestionResult.reused(id, job, source, now);
                })
                .toList());
        if (!plan.reusableByQuestionId().isEmpty()) {
            int reusedCount = plan.reusableByQuestionId().size();
            job.restoreReusedProgress(reusedCount, reusedCount + "개 문항의 이전 첨삭 결과를 재사용합니다.");
        }
        return staged;
    }

    private boolean isSameSource(LlmJob previousJob, String sourceReviewVersionId) {
        if (previousJob.getType() != LlmJobType.COVER_LETTER_RE_REVIEW) {
            return true;
        }
        return previousJob.getRequestRefType() == LlmJobRequestRefType.REVIEW_VERSION
                && sourceReviewVersionId != null
                && sourceReviewVersionId.equals(previousJob.getRequestRefId());
    }

    private boolean isReusable(ReviewJobQuestionResult result, QuestionInput input) {
        if (input == null || result.getStatus() != ReviewJobQuestionResultStatus.COMPLETED) {
            return false;
        }
        CoverLetterQuestion question = input.question();
        return result.getQuestionOrder() == question.getQuestionOrder()
                && result.getQuestionText().equals(question.getQuestion())
                && Integer.valueOf(result.getMaxAnswerLength()).equals(question.getMaxAnswerLength())
                && result.getInputAnswer().equals(input.answer());
    }
}
