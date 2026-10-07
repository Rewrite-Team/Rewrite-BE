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

/**
 * 전체 자기소개서 문맥을 공유하는 문항별 호출을 reviewQuestionExecutor에 제출한다.
 * 각 문항은 LLM 호출·출력 검증 실패에 한해 최대 1회 재시도하고, 성공 결과는 문항별 트랜잭션으로 저장한다.
 * 저장 오류 등 나머지 예외는 Worker의 예상 밖 오류 처리 경계로 전파한다.
 */
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

    /**
     * 문항 task의 종료를 기다린 뒤 요청 문항 순서에서 첫 실패 사유를 반환한다. 분류된 실패가 없으면 empty다.
     * 호출 완료 순서와 대표 오류 선정 순서는 독립적이며, Job 전체의 성공·실패 확정은 Worker가 담당한다.
     */
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
                // 전체 문맥으로 대상 문항 하나를 생성·검증한 뒤에만 저장 트랜잭션을 연다.
                ReviewResult result = reviewClient.reviewQuestion(request, question.questionId());
                // 실행 중 취소된 Job의 늦은 응답은 저장 서비스가 무시하고 전체 확정 단계에서도 취소를 확인한다.
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
