package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

@SpringBootTest
@ActiveProfiles("test")
class FirstReviewJobEventIntegrationTest {

    @Autowired
    private CoverLetterService coverLetterService;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private CoverLetterQuestionRepository questionRepository;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FirstReviewJobWorker worker;

    @MockitoBean
    private ReReviewJobWorker reReviewJobWorker;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        llmJobRepository.deleteAll();
        questionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void submitSchedulesReviewWorkerAfterCommit() {
        Instant now = Instant.parse("2026-06-25T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("job")).willReturn("job_1");
        given(clock.instant()).willReturn(now.plusSeconds(60));
        saveCompleteCoverLetter("cl_1", "user_1", now);

        coverLetterService.submit("cl_1");

        then(worker).should(timeout(1000)).execute("job_1");
        then(reReviewJobWorker).should(never()).execute("job_1");
    }

    @Test
    void submitDoesNotScheduleWorkerWhenReturningExistingReviewJob() {
        Instant now = Instant.parse("2026-06-25T01:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        CoverLetter coverLetter = saveCompleteCoverLetter("cl_1", "user_1", now);
        coverLetter.startReview(now.plusSeconds(30));
        coverLetterRepository.saveAndFlush(coverLetter);
        llmJobRepository.save(LlmJob.pendingReview("job_existing", "cl_1", now.plusSeconds(30), 1));

        coverLetterService.submit("cl_1");

        then(worker).should(never()).execute("job_existing");
    }

    @Test
    void reReviewJobEventSchedulesReReviewWorkerAfterCommit() {
        Instant now = Instant.parse("2026-06-25T01:00:00Z");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            llmJobRepository.save(LlmJob.pendingReReview(
                    "job_1",
                    "cl_1",
                    "직무 키워드를 강조해주세요.",
                    "rv_1",
                    now,
                    1
            ));
            eventPublisher.publishEvent(new LlmJobCreatedEvent("job_1"));
        });

        then(reReviewJobWorker).should(timeout(1000)).execute("job_1");
        then(worker).should(never()).execute("job_1");
    }

    private CoverLetter saveCompleteCoverLetter(String id, String ownerId, Instant now) {
        CoverLetter coverLetter = CoverLetter.draft(id, ownerId, now);
        coverLetter.fillBasicInfo("제목", "회사", "직무", null, now);
        coverLetter.fillPreferences("Spring Boot 경험", now);
        coverLetterRepository.save(coverLetter);

        questionRepository.save(CoverLetterQuestion.create(
                "clq_1",
                coverLetter,
                1,
                "질문",
                1000,
                "답변"
        ));
        return coverLetter;
    }
}
