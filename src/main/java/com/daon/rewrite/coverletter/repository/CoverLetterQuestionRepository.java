package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 원본 문항을 저장 순서대로 읽고, WRITING 문항 step의 전체 교체 시 이전 문항을 제거한다.
 * 소유권·삭제 여부·편집 가능 상태는 먼저 CoverLetter를 조회한 서비스가 확인한다.
 */
public interface CoverLetterQuestionRepository extends JpaRepository<CoverLetterQuestion, String> {

    List<CoverLetterQuestion> findByCoverLetterIdOrderByQuestionOrderAsc(String coverLetterId);

    void deleteByCoverLetter(CoverLetter coverLetter);
}
