package com.daon.rewrite.interview.job;

import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackClient;
import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackClientException;
import com.daon.rewrite.interview.client.feedback.InterviewMessageFeedbackResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;

/**
 * 입력 준비 트랜잭션이 끝나면 전체 피드백을 한 번 생성·검증하고 화면 표시용 조각을 발행한다.
 * 조각 발행 후 별도 트랜잭션에서 assistant 메시지와 Job 완료를 함께 확정한다.
 * 분할 전송 자료는 SSE 서비스의 메모리에 보관하고, 확정된 전체 메시지는 완료 후 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewMessageFeedbackJobWorker {

    private final InterviewMessageFeedbackJobTransactionService transactionService;
    private final InterviewMessageFeedbackClient client;
    private final LlmJobStreamService llmJobStreamService;

    public void execute(String jobId) {
        try {
            InterviewMessageFeedbackWork work = transactionService.start(jobId);
            // 이미 시작했거나 취소·종료된 Job은 다시 실행하지 않는다.
            if (work == null) {
                return;
            }
            InterviewMessageFeedbackResult result = client.generate(work.request());
            // 전체 응답 검증에 성공한 content만 조각으로 발행한다. 최종 저장은 다음 트랜잭션에서 수행한다.
            llmJobStreamService.publishValidatedFeedback(jobId, result.content());
            transactionService.complete(jobId, result);
        } catch (InterviewMessageFeedbackClientException exception) {
            failByClientException(jobId, exception);
        } catch (RuntimeException exception) {
            failByUnexpectedException(jobId, exception);
        }
    }

    // 검증 실패는 delta 전송 전 이 경계로 전달된다. 저장된 USER 답변을 유지한 채 Job만 실패 처리한다.
    private void failByClientException(String jobId, InterviewMessageFeedbackClientException exception) {
        try {
            transactionService.fail(jobId, exception.getReason());
        } catch (RuntimeException failureException) {
            log.error("Failed to mark interview message feedback job as failed. jobId={}", jobId, failureException);
        }
    }

    // 전송·최종 저장 등 예상 밖 오류는 내부 오류로 기록하고 최초 원인과 실패 저장 오류를 각각 남긴다.
    private void failByUnexpectedException(String jobId, RuntimeException exception) {
        try {
            transactionService.failUnexpected(jobId);
        } catch (RuntimeException failureException) {
            log.error("Failed to mark unexpected interview message feedback failure. jobId={}", jobId, failureException);
        }
        log.error("Unexpected interview message feedback failure. jobId={}", jobId, exception);
    }
}
