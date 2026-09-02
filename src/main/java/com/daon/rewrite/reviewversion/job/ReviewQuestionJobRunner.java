package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.reviewversion.client.ReviewClient;
import com.daon.rewrite.reviewversion.client.ReviewClientException;
import com.daon.rewrite.reviewversion.client.ReviewQuestion;
import com.daon.rewrite.reviewversion.client.ReviewRequest;
import com.daon.rewrite.reviewversion.client.ReviewResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
class ReviewQuestionJobRunner {

    private static final int MAX_ATTEMPTS = 2;

    private final ReviewClient reviewClient;
    private final ReviewJobQuestionTransactionService transactionService;
    private final Executor executor;

    ReviewQuestionJobRunner(
            ReviewClient reviewClient,
            ReviewJobQuestionTransactionService transactionService,
            @Qualifier("reviewQuestionExecutor") Executor executor
    ) {
        this.reviewClient = reviewClient;
        this.transactionService = transactionService;
        this.executor = executor;
    }

    Optional<ReviewClientException.Reason> run(String jobId, ReviewRequest request) {
        // 문항별 비동기 작업 생성
        List<CompletableFuture<Optional<ReviewClientException.Reason>>> futures = request.questions().stream()
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
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<ReviewClientException.Reason> reviewQuestion(
            String jobId,
            ReviewRequest request,
            ReviewQuestion question
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ReviewResult result = reviewClient.reviewQuestion(request, question.questionId());
                transactionService.completeQuestion(jobId, result);
                return Optional.empty();
            } catch (ReviewClientException exception) {
                if (attempt < MAX_ATTEMPTS) {
                    transactionService.markRetry(jobId);
                    continue;
                }
                transactionService.failQuestion(jobId, question.questionId());
                return Optional.of(exception.getReason());
            }
        }
        throw new IllegalStateException("첨삭 문항 실행 횟수가 올바르지 않습니다.");
    }
}
