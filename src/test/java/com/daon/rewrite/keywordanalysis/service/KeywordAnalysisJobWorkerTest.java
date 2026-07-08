package com.daon.rewrite.keywordanalysis.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisAnswer;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClient;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisClientException;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisRequest;
import com.daon.rewrite.keywordanalysis.client.KeywordAnalysisResult;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysis;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisKeyword;
import com.daon.rewrite.keywordanalysis.entity.KeywordAnalysisStatus;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisKeywordRepository;
import com.daon.rewrite.keywordanalysis.repository.KeywordAnalysisRepository;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@ActiveProfiles("test")
class KeywordAnalysisJobWorkerTest {

    @Autowired
    private KeywordAnalysisJobWorker worker;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository questionResultRepository;

    @Autowired
    private KeywordAnalysisRepository keywordAnalysisRepository;

    @Autowired
    private KeywordAnalysisKeywordRepository keywordRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @MockitoBean
    private KeywordAnalysisClient keywordAnalysisClient;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        keywordRepository.deleteAll();
        keywordAnalysisRepository.deleteAll();
        questionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        llmJobRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void executeCompletesPendingKeywordAnalysisJobWithFinalAnswers() {
        Instant completedAt = Instant.parse("2026-07-05T01:00:00Z");
        savePendingKeywordAnalysisJob("cl_1", "rv_1", "ka_1", "job_1");
        KeywordAnalysis keywordAnalysis = keywordAnalysisRepository.findById("ka_1").orElseThrow();
        keywordRepository.save(KeywordAnalysisKeyword.of("kak_old", keywordAnalysis, 1, "기존", 70));
        given(keywordAnalysisClient.analyze(any())).willReturn(List.of(
                new KeywordAnalysisResult("백엔드", 95),
                new KeywordAnalysisResult("Spring", 88)
        ));
        given(idGenerator.generate("kak")).willReturn("kak_1", "kak_2");
        given(clock.instant()).willReturn(completedAt);

        worker.execute("job_1");

        ArgumentCaptor<KeywordAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(KeywordAnalysisRequest.class);
        then(keywordAnalysisClient).should().analyze(requestCaptor.capture());
        KeywordAnalysisRequest request = requestCaptor.getValue();
        assertThat(request.title()).isEqualTo("제목");
        assertThat(request.companyName()).isEqualTo("회사");
        assertThat(request.positionTitle()).isEqualTo("직무");
        assertThat(request.preferences()).isEqualTo("Spring Boot 경험");
        assertThat(request.answers())
                .extracting(
                        KeywordAnalysisAnswer::questionId,
                        KeywordAnalysisAnswer::questionOrder,
                        KeywordAnalysisAnswer::question,
                        KeywordAnalysisAnswer::finalAnswer
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("clq_1", 1, "질문 1", "최종 작성본 1"),
                        org.assertj.core.groups.Tuple.tuple("clq_2", 2, "질문 2", "최종 작성본 2")
                );

        assertThat(keywordAnalysisRepository.findById("ka_1")).hasValueSatisfying(analysis -> {
            assertThat(analysis.getStatus()).isEqualTo(KeywordAnalysisStatus.COMPLETED);
            assertThat(analysis.getCompletedAt()).isEqualTo(completedAt);
        });
        assertThat(keywordRepository.findByKeywordAnalysisIdOrderByKeywordOrderAsc("ka_1"))
                .extracting(
                        KeywordAnalysisKeyword::getId,
                        KeywordAnalysisKeyword::getKeywordOrder,
                        KeywordAnalysisKeyword::getKeyword,
                        KeywordAnalysisKeyword::getImportance
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("kak_1", 1, "백엔드", 95),
                        org.assertj.core.groups.Tuple.tuple("kak_2", 2, "Spring", 88)
                );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.KEYWORD_ANALYSIS);
            assertThat(job.getResultRefId()).isEqualTo("ka_1");
            assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        });
    }

    @Test
    void executeFailsKeywordAnalysisWhenProviderFails() {
        Instant failedAt = Instant.parse("2026-07-05T01:00:00Z");
        savePendingKeywordAnalysisJob("cl_1", "rv_1", "ka_1", "job_1");
        given(keywordAnalysisClient.analyze(any()))
                .willThrow(KeywordAnalysisClientException.providerError(new IllegalStateException("provider down")));
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(keywordAnalysisRepository.findById("ka_1")).hasValueSatisfying(analysis -> {
            assertThat(analysis.getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
            assertThat(analysis.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 응답 생성에 실패했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(keywordRepository.findByKeywordAnalysisIdOrderByKeywordOrderAsc("ka_1")).isEmpty();
    }

    @Test
    void executeFailsKeywordAnalysisWhenOutputValidationFails() {
        Instant failedAt = Instant.parse("2026-07-05T01:00:00Z");
        savePendingKeywordAnalysisJob("cl_1", "rv_1", "ka_1", "job_1");
        given(keywordAnalysisClient.analyze(any()))
                .willThrow(KeywordAnalysisClientException.outputValidationFailed());
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(keywordAnalysisRepository.findById("ka_1")).hasValueSatisfying(analysis ->
                assertThat(analysis.getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_OUTPUT_VALIDATION_FAILED");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 출력 형식이 올바르지 않습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeFailsKeywordAnalysisWhenUnexpectedClientExceptionOccurs() {
        Instant failedAt = Instant.parse("2026-07-05T01:00:00Z");
        savePendingKeywordAnalysisJob("cl_1", "rv_1", "ka_1", "job_1");
        given(keywordAnalysisClient.analyze(any()))
                .willThrow(new IllegalStateException("unexpected"));
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertThat(keywordAnalysisRepository.findById("ka_1")).hasValueSatisfying(analysis -> {
            assertThat(analysis.getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
            assertThat(analysis.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("키워드 분석 처리 중 오류가 발생했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeFailsKeywordAnalysisWhenStartPreconditionFails() {
        Instant failedAt = Instant.parse("2026-07-05T01:00:00Z");
        saveProcessingKeywordAnalysisForDraftCoverLetter("cl_1", "ka_1", "job_1");
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        then(keywordAnalysisClient).shouldHaveNoInteractions();
        assertThat(keywordAnalysisRepository.findById("ka_1")).hasValueSatisfying(analysis -> {
            assertThat(analysis.getStatus()).isEqualTo(KeywordAnalysisStatus.FAILED);
            assertThat(analysis.getCompletedAt()).isEqualTo(failedAt);
        });
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("키워드 분석 처리 중 오류가 발생했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeSkipsAlreadyCompletedJob() {
        Instant completedAt = Instant.parse("2026-07-05T01:00:00Z");
        savePendingKeywordAnalysisJob("cl_1", "rv_1", "ka_1", "job_1");
        LlmJob job = llmJobRepository.findById("job_1").orElseThrow();
        job.startProcessing("키워드 분석을 시작합니다.");
        job.markCompleted(1, "키워드 분석이 완료되었습니다.", LlmJobResultRefType.KEYWORD_ANALYSIS, "ka_1", completedAt);
        llmJobRepository.save(job);

        worker.execute("job_1");

        then(keywordAnalysisClient).shouldHaveNoInteractions();
    }

    private void savePendingKeywordAnalysisJob(
            String coverLetterId,
            String reviewVersionId,
            String keywordAnalysisId,
            String jobId
    ) {
        Instant now = Instant.parse("2026-07-05T00:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", now);
        coverLetter.fillBasicInfo("제목", "회사", "직무", null, now);
        coverLetter.fillPreferences("Spring Boot 경험", now);
        coverLetterRepository.save(coverLetter);

        CoverLetterQuestion firstQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "질문 1",
                1000,
                "원본 답변 1"
        ));
        CoverLetterQuestion secondQuestion = questionRepository.save(CoverLetterQuestion.create(
                "clq_2",
                coverLetter,
                2,
                "질문 2",
                1000,
                "원본 답변 2"
        ));

        ReviewVersion reviewVersion = reviewVersionRepository.save(ReviewVersion.first(
                reviewVersionId,
                coverLetter,
                now.plusSeconds(60)
        ));
        ReviewVersionQuestionResult firstResult = ReviewVersionQuestionResult.create(
                "rvqr_1",
                reviewVersion,
                firstQuestion,
                "AI 리포트 1",
                "수정본 1"
        );
        firstResult.updateFinalAnswer("최종 작성본 1");
        ReviewVersionQuestionResult secondResult = ReviewVersionQuestionResult.create(
                "rvqr_2",
                reviewVersion,
                secondQuestion,
                "AI 리포트 2",
                "수정본 2"
        );
        secondResult.updateFinalAnswer("최종 작성본 2");
        questionResultRepository.saveAll(List.of(firstResult, secondResult));

        coverLetter.completeReview(reviewVersion.getId(), now.plusSeconds(90));
        coverLetterRepository.save(coverLetter);

        keywordAnalysisRepository.save(KeywordAnalysis.processing(
                keywordAnalysisId,
                coverLetter,
                reviewVersion.getId(),
                now.plusSeconds(120)
        ));
        llmJobRepository.save(LlmJob.pendingKeywordAnalysis(jobId, coverLetterId, now.plusSeconds(120)));
    }

    private void saveProcessingKeywordAnalysisForDraftCoverLetter(
            String coverLetterId,
            String keywordAnalysisId,
            String jobId
    ) {
        Instant now = Instant.parse("2026-07-05T00:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft(coverLetterId, "user_1", now));
        keywordAnalysisRepository.save(KeywordAnalysis.processing(
                keywordAnalysisId,
                coverLetter,
                "rv_1",
                now.plusSeconds(120)
        ));
        llmJobRepository.save(LlmJob.pendingKeywordAnalysis(jobId, coverLetterId, now.plusSeconds(120)));
    }
}
