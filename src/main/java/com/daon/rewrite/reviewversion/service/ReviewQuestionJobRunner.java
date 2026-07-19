package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.client.FirstReviewClient;
import com.daon.rewrite.reviewversion.client.FirstReviewClientException;
import com.daon.rewrite.reviewversion.client.FirstReviewQuestion;
import com.daon.rewrite.reviewversion.client.FirstReviewRequest;
import com.daon.rewrite.reviewversion.client.FirstReviewResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
class ReviewQuestionJobRunner {

    private static final int MAX_ATTEMPTS = 2;

    private final FirstReviewClient firstReviewClient;
    private final ReviewJobQuestionTransactionService transactionService;
    private final Executor executor;

    ReviewQuestionJobRunner(
            FirstReviewClient firstReviewClient,
            ReviewJobQuestionTransactionService transactionService,
            @Qualifier("reviewQuestionExecutor") Executor executor
    ) {
        this.firstReviewClient = firstReviewClient;
        this.transactionService = transactionService;
        this.executor = executor;
    }

    FirstReviewClientException.Reason run(String jobId, FirstReviewRequest request) {
        // 문항별 비동기 작업 생성
        List<CompletableFuture<QuestionOutcome>> futures = request.questions().stream()
                .map(question -> CompletableFuture.supplyAsync(
                        () -> reviewQuestion(jobId, request, question),
                        executor
                ))
                .toList();

        // 등록한 모든 비동기 문항 작업이 끝날 때까지 현재 스레드를 기다림
        CompletableFuture
                .allOf(futures.toArray(CompletableFuture[]::new))
                .join();  // allof() 가 반환한 Future 가 완료될 때까지 현재 스레드를 대기시킴

        /**
         * CompletableFuture<T> 의 join() 메서드는 다음과 같은 형태로, Future 가 가지고 있는 실제 결과 (T) 를 반환한다.
         * public T join()
         */

        // 모든 문항의 결과를 확인해서 처음 발견된 실패 사유를 반환한다.
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(outcome -> outcome.failureReason() != null)  // 실패한 결과만 남기기
                .map(QuestionOutcome::failureReason)
                .findFirst()    // 첫번째 실패 사유를 Optional<Reason> 으로 반환
                .orElse(null);  // 실패사유가 없으면 null 반환
    }

    private QuestionOutcome reviewQuestion(
            String jobId,
            FirstReviewRequest request,
            FirstReviewQuestion question
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                FirstReviewResult result = firstReviewClient.reviewQuestion(request, question.questionId());
                transactionService.completeQuestion(jobId, result);
                return QuestionOutcome.success();
            } catch (FirstReviewClientException exception) {
                if (attempt < MAX_ATTEMPTS) {
                    transactionService.markRetry(jobId);
                    continue;
                }
                transactionService.failQuestion(jobId, question.questionId());
                return QuestionOutcome.failure(exception.getReason());
            }
        }
        throw new IllegalStateException("첨삭 문항 실행 횟수가 올바르지 않습니다.");
    }

    private record QuestionOutcome(FirstReviewClientException.Reason failureReason) {

        private static QuestionOutcome success() {
            return new QuestionOutcome(null);
        }

        private static QuestionOutcome failure(FirstReviewClientException.Reason reason) {
            return new QuestionOutcome(reason);
        }
    }
}
