package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최초 첨삭과 재첨삭 Job의 시작 → 문항별 실행·저장 → 전체 확정 또는 실패 전환을 조정한다.
 * Job 종류별 차이는 ReviewJobLifecycle 구현이 담당하며, 첨삭이 아닌 Job은 실행하지 않는다.
 * DB 트랜잭션은 각 생명주기 호출에 한정하므로 외부 LLM 응답을 기다리는 동안 시작 시의 행 잠금을 유지하지 않는다.
 */
@Slf4j
@Service
public class ReviewJobExecutor {

    private final LlmJobRepository llmJobRepository;
    private final Map<LlmJobType, ReviewJobLifecycle> lifecycles;
    private final ReviewQuestionJobRunner jobRunner;

    ReviewJobExecutor(
            LlmJobRepository llmJobRepository,
            List<ReviewJobLifecycle> lifecycles,
            ReviewQuestionJobRunner jobRunner
    ) {
        this.llmJobRepository = llmJobRepository;
        this.lifecycles = lifecycles.stream()
                .collect(Collectors.toMap(ReviewJobLifecycle::jobType, Function.identity()));
        this.jobRunner = jobRunner;
    }

    public void execute(String jobId) {
        llmJobRepository.findById(jobId)
                .map(job -> lifecycles.get(job.getType()))
                .ifPresent(lifecycle -> execute(lifecycle, jobId));
    }

    private void execute(ReviewJobLifecycle lifecycle, String jobId) {
        try {
            ReviewJobInput input = lifecycle.start(jobId);
            // 이미 시작되었거나 취소·종료된 Job은 다시 실행하지 않는다.
            if (input == null) {
                return;
            }
            Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, input);
            // 하나라도 실패하면 Job 전체를 실패 처리하되, 성공 문항과 이전 성공 버전은 보존한다.
            if (failureReason.isPresent()) {
                lifecycle.fail(jobId, failureReason.get());
                return;
            }
            // 전체 확정 단계에서 취소 여부를 다시 확인해 늦게 도착한 결과가 취소를 덮어쓰지 않게 한다.
            lifecycle.complete(jobId);
        } catch (RuntimeException exception) {
            // 시작·문항 저장·전체 확정의 예상 밖 오류는 별도 트랜잭션으로 실패 처리하고, 원인과 저장 오류를 각각 남긴다.
            try {
                lifecycle.failUnexpected(jobId);
            } catch (RuntimeException failureException) {
                log.error("첨삭 실패 상태 저장에 실패했습니다. jobId={}, jobType={}",
                        jobId, lifecycle.jobType(), failureException);
            }
            log.error("첨삭 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}, jobType={}",
                    jobId, lifecycle.jobType(), exception);
        }
    }
}
