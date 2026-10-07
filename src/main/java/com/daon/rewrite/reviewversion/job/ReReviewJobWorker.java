package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.service.ReviewVersionCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 요청 시 고정한 최신 성공 버전의 최종 작성본을 입력으로 재첨삭을 실행한다.
 * 시작 트랜잭션이 끝난 뒤 문항별 LLM 호출·저장을 진행하고, 모두 성공하면 기존에 생성한 새 버전을 확정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReReviewJobWorker {

    private final ReReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionCompletionService reviewVersionCompletionService;

    public void execute(String jobId) {
        try {
            ReviewWork work = transactionService.start(jobId);
            // 이미 시작되었거나 취소·종료된 Job은 다시 실행하지 않는다.
            if (work == null) {
                return;
            }
            Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, work.request());
            // 실패 문항이 있으면 이번 Job을 실패 처리하고, 성공 문항과 이전 성공 버전은 보존한다.
            if (failureReason.isPresent()) {
                transactionService.fail(jobId, failureReason.get());
                return;
            }
            // 전체 확정 단계도 취소 여부를 다시 확인해 늦게 도착한 결과가 취소를 덮어쓰지 않게 한다.
            reviewVersionCompletionService.completeReReview(jobId);
        } catch (RuntimeException exception) {
            // 실패 상태 저장까지 실패하더라도 최초 원인과 저장 오류를 각각 남긴다.
            try {
                transactionService.failUnexpected(jobId);
            } catch (RuntimeException failureException) {
                log.error("재첨삭 실패 상태 저장에 실패했습니다. jobId={}", jobId, failureException);
            }
            log.error("재첨삭 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}", jobId, exception);
        }
    }
}
