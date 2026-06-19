package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;

import java.util.List;

public record SaveQuestionsResult(
        CoverLetter coverLetter,
        List<CoverLetterQuestion> questions
) {
}
