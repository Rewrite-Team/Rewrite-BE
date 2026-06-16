package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class CoverLetterServiceTest {

    @Autowired
    private CoverLetterService service;

    @Autowired
    private CoverLetterRepository repository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private IdGenerator idGenerator;

    @MockitoBean
    private Clock clock;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void createDraftPersistsCurrentUserOwnedDraft() {
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        given(currentUserProvider.currentUser()).willReturn(new CurrentUser("user_1", "테스트", null));
        given(idGenerator.generate("cl")).willReturn("cl_fixed");
        given(clock.instant()).willReturn(now);
        given(clock.getZone()).willReturn(seoulZone);

        CoverLetter result = service.createDraft();

        assertThat(result.getId()).isEqualTo("cl_fixed");
        assertThat(result.getOwnerId()).isEqualTo("user_1");
        assertThat(result.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getUpdatedAt()).isEqualTo(now);
        assertThat(repository.findById("cl_fixed")).hasValueSatisfying(saved -> {
            assertThat(saved.getOwnerId()).isEqualTo("user_1");
            assertThat(saved.getStatus()).isEqualTo(CoverLetterStatus.DRAFT);
            assertThat(saved.getCreatedAt()).isEqualTo(now);
            assertThat(saved.getUpdatedAt()).isEqualTo(now);
        });
    }
}
