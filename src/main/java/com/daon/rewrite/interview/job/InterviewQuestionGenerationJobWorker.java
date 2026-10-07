package com.daon.rewrite.interview.job;

import com.daon.rewrite.interview.client.question.InterviewQuestionGenerationClient;
import com.daon.rewrite.interview.client.question.InterviewQuestionGenerationClientException;
import com.daon.rewrite.interview.client.question.InterviewQuestionGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 시작 트랜잭션에서 입력을 준비하고, 외부 호출이 끝나면 별도 트랜잭션으로 질문·대화방을 확정한다.
 * 생성 호출은 한 번 수행하며 LLM 응답 대기 중에는 시작 시의 행 잠금을 유지하지 않는다.
 * 분류된 LLM 실패와 입력 준비·저장 등의 예상 밖 오류를 구분해 실패 상태를 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionGenerationJobWorker {

    private final InterviewQuestionGenerationJobTransactionService transactionService;
    private final InterviewQuestionGenerationClient client;

    public void execute(String jobId) {
        try {
            InterviewQuestionGenerationWork work = transactionService.start(jobId);
            // 이미 시작했거나 취소·종료된 Job은 중복 실행하지 않는다.
            if (work == null) {
                return;
            }
            // 요청 개수·빈 질문·기존 질문과의 중복 검증을 마친 결과만 저장 단계로 넘긴다.
            List<InterviewQuestionGenerationResult> results = client.generate(work.request());
            transactionService.complete(jobId, results);
        } catch (InterviewQuestionGenerationClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    private void failByClientException(String jobId, InterviewQuestionGenerationClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark interview question generation job as failed. jobId={}", jobId, failureException);
        }
    }

    // 실패 상태 저장 자체의 오류와 최초 처리 오류를 각각 남겨 비동기 실행 원인을 보존한다.
    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected interview question generation failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected interview question generation failure. jobId={}", jobId, exception);
    }
}
