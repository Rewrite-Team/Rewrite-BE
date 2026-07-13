package com.daon.rewrite.interview.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewThread;
import com.daon.rewrite.interview.repository.InterviewMessageRepository;
import com.daon.rewrite.interview.repository.InterviewThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private final CurrentUserProvider currentUserProvider;
    private final InterviewThreadRepository interviewThreadRepository;
    private final InterviewMessageRepository interviewMessageRepository;

    @Transactional(readOnly = true)
    public InterviewMessageListResult findMyInterviewMessages(String threadId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        InterviewThread thread = interviewThreadRepository
                .findActiveByIdAndOwnerId(
                        threadId,
                        currentUser.id()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<InterviewMessageItemResult> items = interviewMessageRepository
                .findByThreadIdOrderByCreatedAtAscIdAsc(thread.getId())
                .stream()
                .map(this::toItemResult)
                .toList();

        return new InterviewMessageListResult(thread.getId(), items);
    }

    private InterviewMessageItemResult toItemResult(InterviewMessage message) {
        return new InterviewMessageItemResult(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getFeedbackSummary(),
                message.getFeedbackStrengths(),
                message.getFeedbackImprovements(),
                message.getScore(),
                message.getFollowUpQuestion(),
                message.getCreatedAt()
        );
    }
}
