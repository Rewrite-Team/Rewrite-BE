package com.daon.rewrite.interview.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.entity.InterviewQuestion;
import com.daon.rewrite.interview.entity.InterviewQuestionType;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewThread;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class InterviewMessageRepositoryTest {

    @Autowired
    private InterviewMessageRepository repository;

    @Autowired
    private InterviewThreadRepository interviewThreadRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private CoverLetterRepository coverLetterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindUserAndAssistantMessagesRoundTripsThroughJpa() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        InterviewThread thread = saveThread("it_1", now);
        repository.saveAll(List.of(
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
                        List.of("성과 지표를 추가하세요.", "의사결정 근거를 설명하세요."),
                        78,
                        "가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
                        now.plusSeconds(120)
                )
        ));
        entityManager.flush();
        entityManager.clear();

        List<InterviewMessage> found = repository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1");

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getId()).isEqualTo("im_1");
        assertThat(found.get(0).getThread().getId()).isEqualTo("it_1");
        assertThat(found.get(0).getRole()).isEqualTo(InterviewMessageRole.USER);
        assertThat(found.get(0).getContent()).isEqualTo("저는 프로젝트에서 API 설계를 담당했습니다.");
        assertThat(found.get(0).getFeedbackSummary()).isNull();
        assertThat(found.get(0).getFeedbackStrengths()).isNull();
        assertThat(found.get(0).getFeedbackImprovements()).isNull();
        assertThat(found.get(0).getScore()).isNull();
        assertThat(found.get(0).getFollowUpQuestion()).isNull();
        assertThat(found.get(0).getCreatedAt()).isEqualTo(now.plusSeconds(60));

        assertThat(found.get(1).getId()).isEqualTo("im_2");
        assertThat(found.get(1).getRole()).isEqualTo(InterviewMessageRole.ASSISTANT);
        assertThat(found.get(1).getFeedbackSummary()).isEqualTo("성과와 의사결정 근거를 보강해야 합니다.");
        assertThat(found.get(1).getFeedbackStrengths())
                .containsExactly("담당 역할을 구체적으로 언급했습니다.");
        assertThat(found.get(1).getFeedbackImprovements())
                .containsExactly("성과 지표를 추가하세요.", "의사결정 근거를 설명하세요.");
        assertThat(found.get(1).getScore()).isEqualTo(78);
        assertThat(found.get(1).getFollowUpQuestion())
                .isEqualTo("가장 중요하게 고려한 트레이드오프는 무엇이었나요?");
        assertThat(found.get(1).getCreatedAt()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void findMessagesUsesIdAsTieBreakerWhenCreatedAtIsSame() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        InterviewThread thread = saveThread("it_1", now);
        Instant sameCreatedAt = now.plusSeconds(60);
        repository.saveAll(List.of(
                InterviewMessage.userAnswer("im_b", thread, "두 번째", sameCreatedAt),
                InterviewMessage.userAnswer("im_a", thread, "첫 번째", sameCreatedAt)
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByThreadIdOrderByCreatedAtAscIdAsc("it_1"))
                .extracting(InterviewMessage::getId)
                .containsExactly("im_a", "im_b");
    }

    @Test
    void assistantFeedbackRejectsScoreOutsideOneToOneHundred() {
        Instant now = Instant.parse("2026-07-12T01:00:00Z");
        InterviewThread thread = saveThread("it_1", now);

        assertThatThrownBy(() -> InterviewMessage.assistantFeedback(
                "im_1",
                thread,
                "피드백",
                "요약",
                List.of("강점"),
                List.of("개선점"),
                101,
                "꼬리질문",
                now.plusSeconds(60)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private InterviewThread saveThread(String threadId, Instant now) {
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.draft("cl_1", "user_1", now));
        InterviewSession interviewSession = interviewSessionRepository.save(InterviewSession.questionGenerating(
                "is_1",
                coverLetter,
                "rv_1",
                now.plusSeconds(10)
        ));
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.create(
                "iq_1",
                interviewSession,
                "rv_1",
                1,
                InterviewQuestionType.COVER_LETTER_BASED,
                "프로젝트에서 맡은 역할을 설명해 주세요."
        ));
        return interviewThreadRepository.save(InterviewThread.active(
                threadId,
                interviewSession,
                question,
                now.plusSeconds(20)
        ));
    }
}
