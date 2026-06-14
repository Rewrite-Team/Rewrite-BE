package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.coverletter.repository.InMemoryCoverLetterRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

class CoverLetterServiceTest {

    @Test
    void createDraftCreatesCurrentUserOwnedDraft() {
        CurrentUserProvider currentUserProvider = () -> new CurrentUser("user_1", "테스트", null);
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 14, 0);
        Clock fixedClock = Clock.fixed(now.atZone(seoulZone).toInstant(), seoulZone);

        CoverLetterRepository repository = new InMemoryCoverLetterRepository();
        CoverLetterService service = new CoverLetterService(
                currentUserProvider,
                repository,
                () -> "cl_fixed",
                fixedClock
        );

        CoverLetter result = service.createDraft();

        assertThat(result.getId()).isEqualTo("cl_fixed");
        assertThat(result.getOwnerId()).isEqualTo("user_1");
        assertThat(result.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getUpdatedAt()).isEqualTo(now);
        assertThat(repository.findById("cl_fixed")).containsSame(result);
    }
}
