package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.service.ReviewVersionCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 최초 첨삭의 시작 → 문항별 실행·저장 → 전체 확정 또는 실패 전환을 조정한다.
 * DB 트랜잭션은 각 서비스 호출에 한정하므로 외부 LLM 응답을 기다리는 동안 시작 시의 행 잠금을 유지하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirstReviewJobWorker {

    private final FirstReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionCompletionService reviewVersionCompletionService;

    public void execute(String jobId) {
        try {
            ReviewWork work = transactionService.start(jobId);
            // 이미 시작되었거나 종료된 Job은 다시 실행하지 않는다.
            if (work == null) {
                return;
            }
            Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, work.request());
            // 하나라도 실패하면 Job 전체를 실패 처리하되, 성공 문항은 부분 결과 조회를 위해 보존한다.
            if (failureReason.isPresent()) {
                transactionService.fail(jobId, failureReason.get());
                return;
            }
            // 제출 시 생성한 버전에 성공 문항을 확정하고 최신 성공 버전을 갱신한다.
            reviewVersionCompletionService.completeFirstReview(jobId);
        } catch (RuntimeException exception) {
            // 시작·문항 저장·전체 확정의 예상 밖 오류는 별도 트랜잭션으로 실패 처리하고 원인을 로그에 남긴다.
            try {
                transactionService.failUnexpected(jobId);
            } catch (RuntimeException failureException) {
                log.error("최초 첨삭 실패 상태 저장에 실패했습니다. jobId={}", jobId, failureException);
            }
            log.error("최초 첨삭 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}", jobId, exception);
        }
    }
}
