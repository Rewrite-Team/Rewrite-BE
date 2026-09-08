package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.entity.OAuthLoginState;
import com.daon.rewrite.auth.repository.OAuthLoginStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"prod", "auth-test"})
@RequiredArgsConstructor
public class OAuthStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final OAuthLoginStateRepository repository;
    private final Clock clock;

    @Transactional
    public OAuthStateIssue issue() {
        // expiresAt <= 현재시간 인 OAuthLoginState 데이터 삭제
        // 별도 스케줄러 없이 로그인 요청 시점에 오래된 데이터를 정리하는 lazy cleanup 방식
        repository.deleteByExpiresAtLessThanEqual(Instant.now(clock));
        String state = SecureTokenSupport.randomToken();
        String browserNonce = SecureTokenSupport.randomToken();
        repository.save(OAuthLoginState.create(
                SecureTokenSupport.sha256(state),
                SecureTokenSupport.sha256(browserNonce),
                Instant.now(clock).plus(STATE_TTL)
        ));
        return new OAuthStateIssue(state, browserNonce);
    }


    /*
    로그인 시작 때 저장한 state 와 browserNonce를 검증하고, 성공하면 DB에서 삭제하여 정확히 한 번만 사용되도록 소비하는 매서드

    [Transaction이 필요한 이유]
    요청 A -> state 행 조회 및 잠금 획득
    요청 B -> 같은 행의 잠금이 풀릴 때까지 대기
    요청 A -> state 검증 및 삭제
    요청 A -> 트랜잭션 커밋, 잠금 해제
    요청 B -> 다시 조회했지만 행이 이미 삭제됨
    요청 B -> false 반환
     */
    @Transactional
    public boolean consume(String state, String browserNonce) {
        // state 와 browserNonce가 모두 존재하는지 확인
        if (state == null || state.isBlank() || browserNonce == null || browserNonce.isBlank()) {
            // DB 조회 없이 바로 실패 처리
            return false;
        }

        Instant now = Instant.now(clock);
        return repository.findByStateHashForUpdate(SecureTokenSupport.sha256(state))
                // state 존재 && nonce일치 && 만료되지 않음 일 경우 기존 값 반환
                .filter(loginState -> loginState.canConsume(SecureTokenSupport.sha256(browserNonce), now))
                // 검증에 성공했으므로 해당 DB 레코드 삭제(consume_소비 처리)
                .map(loginState -> {
                    repository.delete(loginState);
                    return true;
                })
                .orElse(false);
    }
}
