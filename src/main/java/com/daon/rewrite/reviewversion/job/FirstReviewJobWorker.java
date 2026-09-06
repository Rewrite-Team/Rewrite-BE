package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.service.ReviewVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirstReviewJobWorker {

    private final FirstReviewJobTransactionService transactionService;
    private final ReviewQuestionJobRunner jobRunner;
    private final ReviewVersionService reviewVersionService;

    public void execute(String jobId) {
        try {
            // Job 시작 처리 & AI응답을 받기 전 임시 처리 결과 객체인 ReviewJobQuestionResult 을 각 문항마다 생성
            ReviewWork work = transactionService.start(jobId);
            // Job이 PENDING 이 아닌 PROCESSING/COMPLETED/FAILED/CANCELED 인 상태라면 실행할 필요 없는 Job으로 판단하고 종료 처리
            // 중복 Worker 실행 방지
            if (work == null) {
                return;
            }
            // 모든 문항을 AI로 첨삭 진행
            // ReviewQuestionJobRunner는 자소서 문항마다 비동기 작업을 생성해 병렬로 실행
            // 전부 성공하면 Optional.empty() 반환
            // 하나 이상 실패하면, 최초 실패 사유를 담은 Optional 반환
            Optional<ReviewClientException.Reason> failureReason = jobRunner.run(jobId, work.request());
            // 문항 하나라도 최종 실패했다면 최초 첨삭 전체를 실패 처리 (이미 성공한 문항의 임시 결과는 남아있기에 부분 결과 조회 가능)
            // LlmJob.status -> FAILED
            // CoverLetter.status -> REVIEW_FAILED
            if (failureReason.isPresent()) {
                transactionService.fail(jobId, failureReason.get());
                return;
            }
            // 모든 문항이 성공했다면 임시 결과를 정식 첨삭 버전으로 확정
            /*
             * ReviewVersion 생성
             * 각 임시 문항 결과를 ReviewVersionQuestionResult로 복사
             * CoverLetter.status -> REVIEWED
             * latestReviewedVersionId 설정
             * LlmJob.status -> COMPLETED
             * Job 결과 참조에 생성된 ReviewVersion ID 설정
             */
            reviewVersionService.completeFirstReview(jobId);
        } catch (RuntimeException exception) {
            try {
                transactionService.failUnexpected(jobId);
            } catch (RuntimeException failureException) {
                log.error("최초 첨삭 실패 상태 저장에 실패했습니다. jobId={}", jobId, failureException);
            }
            log.error("최초 첨삭 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}", jobId, exception);
        }
    }
}
