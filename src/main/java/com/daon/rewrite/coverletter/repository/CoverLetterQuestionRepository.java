package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoverLetterQuestionRepository extends JpaRepository<CoverLetterQuestion, String> {

    List<CoverLetterQuestion> findByCoverLetterIdOrderByQuestionOrderAsc(String coverLetterId);

    void deleteByCoverLetter(CoverLetter coverLetter);
}
