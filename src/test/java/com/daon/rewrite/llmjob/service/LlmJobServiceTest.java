package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobResultRefType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class LlmJobServiceTest {

    @Autowired
    private LlmJobService service;

    @Autowired
    private LlmJobRepository llmJobRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @AfterEach
    void cleanUp() {
        llmJobRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void findMyJobReturnsCurrentUserCoverLetterJob() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        LlmJob job = llmJobRepository.save(LlmJob.pendingReview("job_1", "cl_1", now, 3));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        LlmJob result = service.findMyJob("job_1");

        assertThat(result.getId()).isEqualTo(job.getId());
        assertThat(result.getTargetId()).isEqualTo("cl_1");
    }

    @Test
    void findMyJobThrowsNotFoundWhenJobIsMissing() {
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.findMyJob("job_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void findMyJobThrowsNotFoundWhenCoverLetterBelongsToOtherOwner() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_other", "user_2", now));
        llmJobRepository.save(LlmJob.pendingReview("job_other", "cl_other", now, 3));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.findMyJob("job_other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void findMyJobThrowsNotFoundWhenCoverLetterIsDeleted() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        CoverLetter deleted = CoverLetter.draft("cl_deleted", "user_1", now);
        deleted.markDeleted(now.plusSeconds(60));
        coverLetterRepository.save(deleted);
        llmJobRepository.save(LlmJob.pendingReview("job_deleted", "cl_deleted", now, 3));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThatThrownBy(() -> service.findMyJob("job_deleted"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void findMyJobReturnsCompletedAndFailedJobsForDtoConversion() {
        Instant now = Instant.parse("2026-06-20T05:10:00Z");
        coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        LlmJob completed = LlmJob.pendingReview("job_completed", "cl_1", now, 3);
        completed.markCompleted(3, "첨삭이 완료되었습니다.", LlmJobResultRefType.REVIEW_VERSION, "rv_1", now.plusSeconds(60));
        LlmJob failed = LlmJob.pendingReview("job_failed", "cl_1", now, 3);
        failed.markFailed(0, "LLM 첨삭에 실패했습니다.", "LLM_PROVIDER_ERROR", "LLM 응답 생성에 실패했습니다.", now.plusSeconds(120));
        llmJobRepository.save(completed);
        llmJobRepository.save(failed);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertThat(service.findMyJob("job_completed").getResultRefId()).isEqualTo("rv_1");
        assertThat(service.findMyJob("job_failed").getErrorCode()).isEqualTo("LLM_PROVIDER_ERROR");
    }
}
