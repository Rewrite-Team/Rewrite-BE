package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.config.FrontendTarget;
import com.daon.rewrite.auth.entity.OAuthLoginState;
import com.daon.rewrite.auth.repository.OAuthLoginStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시작 요청과 callback을 연결할 일회성 state·브라우저 nonce를 관리한다.
 * state는 카카오 왕복 query에, nonce는 시작 브라우저의 Cookie에 담기며 DB에는 두 값의 해시만 저장한다.
 * 복귀할 프론트엔드 환경도 state에 포함해 callback 검증 후 복원한다.
 */
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class OAuthStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final OAuthLoginStateRepository repository;
    private final Clock clock;

    /**
     * 허용된 프론트엔드 환경을 난수 state의 접미부에 넣고, 별도의 브라우저 nonce와 함께 발급한다.
     * 환경을 포함한 state 전체의 해시를 저장하므로 callback에서 접미부만 바꾼 값도 검증을 통과하지 못한다.
     * 반환한 두 원문은 카카오 URL과 브라우저 Cookie에 각각 사용하며, 저장된 요청은 5분 동안 유효하다.
     */
    @Transactional
    public StateIssue issue(FrontendTarget frontendTarget) {
        // 별도 스케줄러 없이 새 로그인 요청 시점에 만료된 state를 정리한다.
        repository.deleteByExpiresAtLessThanEqual(Instant.now(clock));
        String state = SecureTokenSupport.randomToken() + "." + frontendTarget.value();
        String browserNonce = SecureTokenSupport.randomToken();
        repository.save(OAuthLoginState.create(
                SecureTokenSupport.sha256(state),
                SecureTokenSupport.sha256(browserNonce),
                Instant.now(clock).plus(STATE_TTL)
        ));
        return new StateIssue(state, browserNonce);
    }


    /**
     * state 해시로 찾은 요청의 nonce와 만료 시각을 검증하고, 일치하면 행을 삭제한 뒤 복귀 환경을 반환한다.
     * 조회 잠금부터 삭제까지 한 트랜잭션으로 묶어 동시 callback이 같은 state를 중복 소비하지 못하게 한다.
     * 누락·불일치·만료·재사용 또는 해석할 수 없는 환경은 빈 Optional로 반환한다.
     * 카카오 조회·사용자 저장보다 먼저 소비하므로 이후 로그인 처리가 실패해도 같은 state는 재사용할 수 없다.
     */
    @Transactional
    public Optional<FrontendTarget> consume(String state, String browserNonce) {
        if (state == null || state.isBlank() || browserNonce == null || browserNonce.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        return repository.findByStateHashForUpdate(SecureTokenSupport.sha256(state))
                .filter(loginState -> loginState.canConsume(SecureTokenSupport.sha256(browserNonce), now))
                .flatMap(loginState -> {
                    repository.delete(loginState);
                    return frontendTarget(state);
                });
    }

    private static Optional<FrontendTarget> frontendTarget(String state) {
        int separatorIndex = state.lastIndexOf('.');
        if (separatorIndex < 0) {
            // 환경 접미부가 없는 state는 기존 운영 로그인 흐름으로 해석한다.
            return Optional.of(FrontendTarget.PRODUCTION);
        }
        if (separatorIndex == state.length() - 1) {
            return Optional.empty();
        }
        return FrontendTarget.from(state.substring(separatorIndex + 1));
    }

    public record StateIssue(
            String state,
            String browserNonce
    ) {
    }
}
