package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.interview.entity.InterviewMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * thread 대화를 생성 시각·ID 오름차순으로 읽어 화면과 LLM 문맥의 순서를 맞춘다.
 * 최신 USER 메시지 조회는 해당 답변의 피드백 Job 복구에 사용하며, thread 접근 권한은 상위 서비스가 확인한다.
 */
public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, String> {

    List<InterviewMessage> findByThreadIdOrderByCreatedAtAscIdAsc(String threadId);

    Optional<InterviewMessage> findFirstByThreadIdAndRoleOrderByCreatedAtDescIdDesc(
            String threadId,
            InterviewMessageRole role
    );
}
