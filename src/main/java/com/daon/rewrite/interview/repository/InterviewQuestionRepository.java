package com.daon.rewrite.interview.repository;

import com.daon.rewrite.interview.entity.InterviewQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 전체 생성 순서 조회는 추가 질문의 중복 방지 문맥과 다음 order 결정에 사용한다.
 * 화면 목록은 cursor order 미만의 질문을 내림차순으로 읽어 최신 질문부터 이어서 조회한다.
 * 세션 소유권과 부모 자기소개서 삭제 여부는 호출 서비스가 확인한다.
 */
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, String> {

    List<InterviewQuestion> findByInterviewSessionIdOrderByQuestionOrderAsc(String interviewSessionId);

    List<InterviewQuestion> findByInterviewSessionIdAndQuestionOrderLessThanOrderByQuestionOrderDesc(
            String interviewSessionId,
            int questionOrder,
            Pageable pageable
    );
}
