package com.daon.rewrite.keywordanalysis.job;

import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClient;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClientException;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 시작 트랜잭션에서 입력을 준비한 뒤 외부 분석을 호출하고 별도 트랜잭션으로 완료·실패를 저장한다.
 * LLM 응답 대기 중에는 시작 시의 행 잠금을 유지하지 않는다.
 * 이 Worker는 분석을 한 번 호출하며, 분류된 LLM 오류와 예상 밖 오류를 각 실패 처리 경계로 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordAnalysisJobWorker {

    private final KeywordAnalysisJobTransactionService transactionService;
    private final KeywordAnalysisClient keywordAnalysisClient;

    public void execute(String jobId) {
        try {
            KeywordAnalysisWork work = transactionService.start(jobId);
            // 이미 시작했거나 취소·종료된 Job은 다시 실행하지 않는다.
            if (work == null) {
                return;
            }
            // 전체 결과의 파싱·검증을 마친 목록만 완료 트랜잭션에 전달한다.
            List<KeywordAnalysisResult> results = keywordAnalysisClient.analyze(work.request());
            transactionService.complete(jobId, results);
        } catch (KeywordAnalysisClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    // provider·출력 검증 실패 사유를 Job에 기록하며, 실패 저장 자체의 오류도 로그에 남긴다.
    private void failByClientException(String jobId, KeywordAnalysisClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark keyword analysis job as failed. jobId={}", jobId, failureException);
        }
    }

    // 시작·완료 저장 등 예상 밖 오류는 내부 오류로 처리하고 최초 원인과 실패 저장 오류를 각각 보존한다.
    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected keyword analysis job failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected keyword analysis job failure. jobId={}", jobId, exception);
    }
}
