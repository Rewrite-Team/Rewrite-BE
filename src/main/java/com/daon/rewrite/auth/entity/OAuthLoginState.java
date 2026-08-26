package com.daon.rewrite.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
// 로그인을 시작할 때 repository.deleteByExpiresAtLessThanEqual(now); 쿼리로 만료된 데이터를 정리하기 때문에 expires_at에 인덱스 생성
@Table(
        name = "oauth_login_states",
        indexes = @Index(name = "idx_oauth_login_states_expires_at", columnList = "expires_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 로그인 시작부터 카카오 콜백까지 5분만 유지되는 임시 데이터를 저장하는 테이블
public class OAuthLoginState {

    // state원문은 카카오 인증 URL의 query parameter로 전달
    @Id
    @Column(name = "state_hash", nullable = false, updatable = false, length = 64)
    private String stateHash;

    // browserNonce 원문은 로그인을 시작한 브라우저의 HttpOnly Cookie로 전달
    @Column(name = "browser_nonce_hash", nullable = false, updatable = false, length = 64)
    private String browserNonceHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    private OAuthLoginState(String stateHash, String browserNonceHash, Instant expiresAt) {
        this.stateHash = stateHash;
        this.browserNonceHash = browserNonceHash;
        this.expiresAt = expiresAt;
    }

    public static OAuthLoginState create(String stateHash, String browserNonceHash, Instant expiresAt) {
        return new OAuthLoginState(stateHash, browserNonceHash, expiresAt);
    }

    public boolean canConsume(String expectedBrowserNonceHash, Instant now) {
        return expiresAt.isAfter(now)   // 만료시간 검증
                && java.security.MessageDigest.isEqual( // DB에 저장된 nonce 해시와 브라우저 쿠키의 원본 nonce 해시값의 동일 여부 비교
                browserNonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedBrowserNonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }
}
