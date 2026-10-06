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
        List<CompletableFuture<Optional<ReviewClientException.Reason>>> futures = request.questions().stream()
                .map(question -> CompletableFuture.supplyAsync(
                        () -> reviewQuestion(jobId, request, question),
                        executor
                ))
                .toList();

        // 한 문항이 실패해도 나머지 문항의 부분 결과를 보존하도록 모두 끝날 때까지 기다린다.
        CompletableFuture
                .allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        // 여러 문항이 실패하면 요청 문항 순서에서 첫 실패 사유를 대표로 사용한다.
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
                // 외부 호출 중 DB 트랜잭션을 유지하지 않도록 문항 결과 저장을 별도 서비스에 맡긴다.
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
