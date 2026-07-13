package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewMessageRepository;
import com.daon.rewrite.interview.repository.InterviewQuestionRepository;
import com.daon.rewrite.interview.repository.InterviewSessionRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class InterviewMessageServiceTest {

    @Autowired
    private InterviewMessageService service;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    @Autowired
    private InterviewThreadRepository interviewThreadRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @AfterEach
    void cleanUp() {
        interviewMessageRepository.deleteAll();
        interviewThreadRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        coverLetterRepository.deleteAll();
    }

    @Test
    void findMyInterviewMessagesReturnsUserAndAssistantMessagesInOrder() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        InterviewThread thread = saveThread("it_1", "cl_1", "user_1", false, now);
        interviewMessageRepository.saveAll(List.of(
                InterviewMessage.userAnswer(
                        "im_1",
                        thread,
                        "저는 프로젝트에서 API 설계를 담당했습니다.",
                        now.plusSeconds(60)
                ),
                InterviewMessage.assistantFeedback(
                        "im_2",
                        thread,
                        "역할은 명확하지만 성과 설명이 부족합니다.",
                        "성과와 의사결정 근거를 보강해야 합니다.",
                        List.of("담당 역할을 구체적으로 언급했습니다."),
                        List.of("성과 지표를 추가하세요."),
                        78,
                        "가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
                        now.plusSeconds(120)
                )
        ));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        InterviewMessageListResult result = service.findMyInterviewMessages("it_1");

        assertThat(result.threadId()).isEqualTo("it_1");
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).role()).isEqualTo(InterviewMessageRole.USER);
        assertThat(result.items().get(0).feedbackSummary()).isNull();
        assertThat(result.items().get(0).score()).isNull();
        assertThat(result.items().get(1).role()).isEqualTo(InterviewMessageRole.ASSISTANT);
        assertThat(result.items().get(1).feedbackStrengths())
                .containsExactly("담당 역할을 구체적으로 언급했습니다.");
        assertThat(result.items().get(1).feedbackImprovements())
                .containsExactly("성과 지표를 추가하세요.");
        assertThat(result.items().get(1).score()).isEqualTo(78);
        assertThat(result.items().get(1).followUpQuestion())
                .isEqualTo("가장 중요하게 고려한 트레이드오프는 무엇이었나요?");
    }

    @Test
    void findMyInterviewMessagesReturnsEmptyItemsForNewThread() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        saveThread("it_1", "cl_1", "user_1", false, now);
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        InterviewMessageListResult result = service.findMyInterviewMessages("it_1");

        assertThat(result.threadId()).isEqualTo("it_1");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void findMyInterviewMessagesRejectsMissingOtherOwnerOrDeletedThread() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        saveThread("it_other", "cl_other", "user_2", false, now);
        saveThread("it_deleted", "cl_deleted", "user_1", true, now.plusSeconds(300));
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));

        assertNotFound("it_missing");
        assertNotFound("it_other");
        assertNotFound("it_deleted");
    }

    private void assertNotFound(String threadId) {
        assertThatThrownBy(() -> service.findMyInterviewMessages(threadId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private InterviewThread saveThread(
            String threadId,
            String coverLetterId,
            String ownerId,
            boolean deleted,
            Instant now
    ) {
        CoverLetter coverLetter = CoverLetter.draft(coverLetterId, ownerId, now);
        if (deleted) {
            coverLetter.markDeleted(now.plusSeconds(10));
        }
        coverLetterRepository.save(coverLetter);
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_" + threadId,
                coverLetter,
                "rv_" + threadId,
                now.plusSeconds(20)
        ));
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.create(
                "iq_" + threadId,
                interviewSession,
                "rv_" + threadId,
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        ));
        return interviewThreadRepository.save(InterviewThread.active(
                threadId,
                interviewSession,
                question,
                now.plusSeconds(30)
        ));
    }
}
