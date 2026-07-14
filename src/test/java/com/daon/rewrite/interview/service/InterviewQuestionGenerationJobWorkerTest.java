package com.daon.rewrite.interview.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationAnswer;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationClient;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationClientException;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationRequest;
import com.daon.rewrite.interview.client.InterviewQuestionGenerationResult;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.entity.InterviewThreadStatus;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
@ActiveProfiles("test")
class InterviewQuestionGenerationJobWorkerTest {

    @Autowired
    private InterviewQuestionGenerationJobWorker worker;

    @Autowired
    private InterviewQuestionGenerationJobTransactionService transactionService;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository coverLetterQuestionRepository;

    @Autowired
    private ReviewVersionRepository reviewVersionRepository;

    @Autowired
    private ReviewVersionQuestionResultRepository reviewVersionQuestionResultRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @MockitoSpyBean
    private InterviewThreadRepository interviewThreadRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @MockitoBean
    private InterviewQuestionGenerationClient client;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        interviewThreadRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        reviewVersionQuestionResultRepository.deleteAll();
        reviewVersionRepository.deleteAll();
        llmJobRepository.deleteAll();
        coverLetterQuestionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void executeCompletesPendingJobAndStoresFiveQuestionsWithThreads() {
        Instant completedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        given(client.generate(any())).willReturn(validResults());
        given(idGenerator.generate("iq")).willReturn("iq_1", "iq_2", "iq_3", "iq_4", "iq_5");
        given(idGenerator.generate("it")).willReturn("it_1", "it_2", "it_3", "it_4", "it_5");
        given(clock.instant()).willReturn(completedAt);

        worker.execute("job_1");

        ArgumentCaptor<InterviewQuestionGenerationRequest> requestCaptor = ArgumentCaptor.forClass(
                InterviewQuestionGenerationRequest.class
        );
        then(client).should().generate(requestCaptor.capture());
        InterviewQuestionGenerationRequest request = requestCaptor.getValue();
        assertThat(request.companyName()).isEqualTo("회사");
        assertThat(request.positionTitle()).isEqualTo("백엔드 개발자");
        assertThat(request.preferences()).isEqualTo("Spring Boot 경험");
        assertThat(request.answers())
                .extracting(
                        InterviewQuestionGenerationAnswer::questionId,
                        InterviewQuestionGenerationAnswer::questionOrder,
                        InterviewQuestionGenerationAnswer::question,
                        InterviewQuestionGenerationAnswer::finalAnswer
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("clq_1", 1, "질문 1", "최종 작성본 1"),
                        org.assertj.core.groups.Tuple.tuple("clq_2", 2, "질문 2", "최종 작성본 2")
                );

        assertThat(interviewSessionRepository.findById("is_1")).hasValueSatisfying(session ->
                assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.ACTIVE)
        );
        assertThat(interviewQuestionRepository.findByInterviewSessionIdOrderByQuestionOrderAsc("is_1"))
                .extracting(
                        InterviewQuestion::getId,
                        InterviewQuestion::getSourceReviewVersionId,
                        InterviewQuestion::getQuestionOrder,
                        InterviewQuestion::getType,
                        InterviewQuestion::getQuestion
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_1", "rv_1", 1, InterviewQuestionType.COVER_LETTER_BASED, "경험 질문 1"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_2", "rv_1", 2, InterviewQuestionType.COVER_LETTER_BASED, "경험 질문 2"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_3", "rv_1", 3, InterviewQuestionType.COVER_LETTER_BASED, "경험 질문 3"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_4", "rv_1", 4, InterviewQuestionType.COVER_LETTER_BASED, "경험 질문 4"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "iq_5", "rv_1", 5, InterviewQuestionType.COVER_LETTER_BASED, "경험 질문 5"
                        )
                );
        assertThat(interviewThreadRepository.findByInterviewSessionId("is_1"))
                .extracting(
                        InterviewThread::getId,
                        thread -> thread.getInterviewSession().getId(),
                        thread -> thread.getInterviewQuestion().getId(),
                        InterviewThread::getStatus,
                        InterviewThread::getCreatedAt
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "it_1", "is_1", "iq_1", InterviewThreadStatus.ACTIVE, completedAt
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "it_2", "is_1", "iq_2", InterviewThreadStatus.ACTIVE, completedAt
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "it_3", "is_1", "iq_3", InterviewThreadStatus.ACTIVE, completedAt
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "it_4", "is_1", "iq_4", InterviewThreadStatus.ACTIVE, completedAt
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "it_5", "is_1", "iq_5", InterviewThreadStatus.ACTIVE, completedAt
                        )
                );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(job.getProgressCurrent()).isEqualTo(5);
            assertThat(job.getResultRefType()).isEqualTo(LlmJobResultRefType.INTERVIEW_SESSION);
            assertThat(job.getResultRefId()).isEqualTo("is_1");
            assertThat(job.getCompletedAt()).isEqualTo(completedAt);
        });
    }

    @Test
    void executeFailsSessionAndJobWhenProviderFails() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        given(client.generate(any())).willThrow(
                InterviewQuestionGenerationClientException.providerError(new IllegalStateException("provider down"))
        );
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertFailedSessionAndEmptyQuestions("is_1");
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 응답 생성에 실패했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeFailsSessionAndJobWhenOutputValidationFails() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        given(client.generate(any()))
                .willThrow(InterviewQuestionGenerationClientException.outputValidationFailed());
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertFailedSessionAndEmptyQuestions("is_1");
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_OUTPUT_VALIDATION_FAILED");
            assertThat(job.getErrorMessage()).isEqualTo("LLM 출력 형식이 올바르지 않습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void failMarksJobFailedWhenSessionIsAlreadyActive() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        transactionService.start("job_1");
        InterviewSession interviewSession = interviewSessionRepository.findById("is_1").orElseThrow();
        interviewSession.activate();
        interviewSessionRepository.saveAndFlush(interviewSession);
        given(clock.instant()).willReturn(failedAt);

        transactionService.fail(
                "job_1",
                InterviewQuestionGenerationClientException.Reason.PROVIDER_ERROR
        );

        assertThat(interviewSessionRepository.findById("is_1")).hasValueSatisfying(session ->
                assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.ACTIVE)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void failMarksJobFailedWhenSessionDoesNotExist() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        transactionService.start("job_1");
        interviewSessionRepository.deleteById("is_1");
        interviewSessionRepository.flush();
        given(clock.instant()).willReturn(failedAt);

        transactionService.fail(
                "job_1",
                InterviewQuestionGenerationClientException.Reason.OUTPUT_VALIDATION_FAILED
        );

        assertThat(interviewSessionRepository.findById("is_1")).isEmpty();
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LLM_OUTPUT_VALIDATION_FAILED");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeFailsSessionAndJobWhenUnexpectedClientExceptionOccurs() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        given(client.generate(any())).willThrow(new IllegalStateException("unexpected"));
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        assertFailedSessionAndEmptyQuestions("is_1");
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("면접 질문 생성 처리 중 오류가 발생했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeRollsBackQuestionsAndThreadsWhenThreadPersistenceFails() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        given(client.generate(any())).willReturn(validResults());
        given(idGenerator.generate("iq")).willReturn("iq_1", "iq_2", "iq_3", "iq_4", "iq_5");
        given(idGenerator.generate("it")).willReturn("it_1", "it_2", "it_3", "it_4", "it_5");
        given(clock.instant()).willReturn(failedAt);
        willThrow(new DataIntegrityViolationException("thread persistence failed"))
                .given(interviewThreadRepository)
                .saveAll(any());

        worker.execute("job_1");

        assertFailedSessionAndEmptyQuestions("is_1");
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeFailsSessionAndJobWhenStartPreconditionFails() {
        Instant failedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingJobForDraftCoverLetter("cl_1", "is_1", "job_1");
        given(clock.instant()).willReturn(failedAt);

        worker.execute("job_1");

        then(client).shouldHaveNoInteractions();
        assertFailedSessionAndEmptyQuestions("is_1");
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(job -> {
            assertThat(job.getStatus()).isEqualTo(LlmJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(job.getErrorMessage()).isEqualTo("면접 질문 생성 처리 중 오류가 발생했습니다.");
            assertThat(job.getCompletedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void executeSkipsAlreadyCompletedJobWithoutDuplicatingQuestions() {
        Instant completedAt = Instant.parse("2026-07-10T12:00:00Z");
        savePendingInterviewQuestionGenerationJob("cl_1", "rv_1", "is_1", "job_1");
        InterviewSession interviewSession = interviewSessionRepository.findById("is_1").orElseThrow();
        interviewSession.activate();
        interviewSessionRepository.save(interviewSession);
        List<InterviewQuestion> existingQuestions = new ArrayList<>();
        List<InterviewQuestionGenerationResult> existingResults = validResults();
        for (int index = 0; index < existingResults.size(); index++) {
            InterviewQuestionGenerationResult result = existingResults.get(index);
            existingQuestions.add(InterviewQuestion.create(
                    "iq_existing_" + (index + 1),
                    interviewSession,
                    "rv_1",
                    index + 1,
                    InterviewQuestionType.COVER_LETTER_BASED,
                    result.question()
            ));
        }
        interviewQuestionRepository.saveAll(existingQuestions);
        List<InterviewThread> existingThreads = new ArrayList<>();
        for (int index = 0; index < existingQuestions.size(); index++) {
            existingThreads.add(InterviewThread.active(
                    "it_existing_" + (index + 1),
                    interviewSession,
                    existingQuestions.get(index),
                    completedAt
            ));
        }
        interviewThreadRepository.saveAll(existingThreads);
        LlmJob job = llmJobRepository.findById("job_1").orElseThrow();
        job.startProcessing("면접 질문 생성을 시작합니다.");
        job.markCompleted(
                5,
                "면접 질문 생성이 완료되었습니다.",
                LlmJobResultRefType.INTERVIEW_SESSION,
                "is_1",
                completedAt
        );
        llmJobRepository.save(job);

        worker.execute("job_1");

        then(client).shouldHaveNoInteractions();
        assertThat(interviewQuestionRepository.findByInterviewSessionIdOrderByQuestionOrderAsc("is_1"))
                .extracting(InterviewQuestion::getId)
                .containsExactly(
                        "iq_existing_1",
                        "iq_existing_2",
                        "iq_existing_3",
                        "iq_existing_4",
                        "iq_existing_5"
                );
        assertThat(interviewThreadRepository.findByInterviewSessionId("is_1"))
                .extracting(InterviewThread::getId)
                .containsExactlyInAnyOrder(
                        "it_existing_1",
                        "it_existing_2",
                        "it_existing_3",
                        "it_existing_4",
                        "it_existing_5"
                );
        assertThat(interviewSessionRepository.findById("is_1")).hasValueSatisfying(session ->
                assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.ACTIVE)
        );
        assertThat(llmJobRepository.findById("job_1")).hasValueSatisfying(reloadedJob -> {
            assertThat(reloadedJob.getStatus()).isEqualTo(LlmJobStatus.COMPLETED);
            assertThat(reloadedJob.getResultRefType()).isEqualTo(LlmJobResultRefType.INTERVIEW_SESSION);
            assertThat(reloadedJob.getResultRefId()).isEqualTo("is_1");
            assertThat(reloadedJob.getCompletedAt()).isEqualTo(completedAt);
        });
    }

    private List<InterviewQuestionGenerationResult> validResults() {
        return List.of(
                new InterviewQuestionGenerationResult("경험 질문 1"),
                new InterviewQuestionGenerationResult("경험 질문 2"),
                new InterviewQuestionGenerationResult("경험 질문 3"),
                new InterviewQuestionGenerationResult("경험 질문 4"),
                new InterviewQuestionGenerationResult("경험 질문 5")
        );
    }

    private void assertFailedSessionAndEmptyQuestions(String interviewSessionId) {
        assertThat(interviewSessionRepository.findById(interviewSessionId)).hasValueSatisfying(session ->
                assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.FAILED)
        );
        assertThat(interviewQuestionRepository
                .findByInterviewSessionIdOrderByQuestionOrderAsc(interviewSessionId))
                .isEmpty();
        assertThat(interviewThreadRepository.findByInterviewSessionId(interviewSessionId)).isEmpty();
    }

    private void savePendingInterviewQuestionGenerationJob(
            String coverLetterId,
            String reviewVersionId,
            String interviewSessionId,
            String jobId
    ) {
        Instant now = Instant.parse("2026-07-10T11:00:00Z");
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, "user_1", now);
        coverLetter.fillBasicInfo("제목", "회사", "백엔드 개발자", null, now);
        coverLetter.fillPreferences("Spring Boot 경험", now);
        coverLetterRepository.save(coverLetter);

        CoverLetterQuestion firstQuestion = coverLetterQuestionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "질문 1",
                1000,
                "원본 답변 1"
        ));
        CoverLetterQuestion secondQuestion = coverLetterQuestionRepository.save(CoverLetterQuestion.create(
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
        reviewVersionQuestionResultRepository.saveAll(List.of(firstResult, secondResult));

        coverLetter.completeReview(reviewVersion.getId(), now.plusSeconds(90));
        coverLetterRepository.save(coverLetter);
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                interviewSessionId,
                coverLetter,
                reviewVersion.getId(),
                now.plusSeconds(120)
        ));
        llmJobRepository.save(LlmJob.pendingInterviewQuestionGeneration(
                jobId,
                coverLetterId,
                now.plusSeconds(120)
        ));
    }

    private void savePendingJobForDraftCoverLetter(
            String coverLetterId,
            String interviewSessionId,
            String jobId
    ) {
        Instant now = Instant.parse("2026-07-10T11:00:00Z");
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft(
                coverLetterId,
                "user_1",
                now
        ));
        interviewSessionRepository.save(InterviewSession.questionGenerating(
                interviewSessionId,
                coverLetter,
                "rv_missing",
                now.plusSeconds(120)
        ));
        llmJobRepository.save(LlmJob.pendingInterviewQuestionGeneration(
                jobId,
                coverLetterId,
                now.plusSeconds(120)
        ));
    }
}
