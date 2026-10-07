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

/**
 * 카카오 로그인 시작 요청과 callback을 연결하는 5분 수명의 검증 정보다.
 * state와 브라우저 nonce는 SHA-256 해시만 저장하고, 원문은 각각 카카오 요청과 HttpOnly Cookie로 전달한다.
 * callback에서 검증을 통과한 행은 서비스가 삭제하므로 같은 로그인 요청을 다시 사용할 수 없다.
 */
@Getter
@Entity
// 새 로그인 시작 시 만료된 행을 정리하는 조회를 지원한다.
@Table(
        name = "oauth_login_states",
        indexes = @Index(name = "idx_oauth_login_states_expires_at", columnList = "expires_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthLoginState {

    @Id
    @Column(name = "state_hash", nullable = false, updatable = false, length = 64)
    private String stateHash;

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

    /**
     * 만료 전이고 같은 브라우저 nonce인지 판단하며 이 메서드 자체는 행을 삭제하지 않는다.
     * 일회성은 서비스가 잠금 조회와 검증·삭제를 같은 트랜잭션에서 수행해 보장한다.
     */
    public boolean canConsume(String expectedBrowserNonceHash, Instant now) {
        return expiresAt.isAfter(now)
                && java.security.MessageDigest.isEqual(
                browserNonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedBrowserNonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }
}
