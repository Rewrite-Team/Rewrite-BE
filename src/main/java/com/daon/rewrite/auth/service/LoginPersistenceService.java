package com.daon.rewrite.auth.service;

import com.daon.rewrite.auth.AuthProvider;
import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.entity.RefreshToken;
import com.daon.rewrite.auth.entity.User;
import com.daon.rewrite.auth.repository.RefreshTokenRepository;
import com.daon.rewrite.auth.repository.UserRepository;
import com.daon.rewrite.global.util.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 조회 결과를 Rewrite 사용자에 반영하고 로그인에 사용할 인증 토큰을 준비한다.
 * 외부 카카오 요청이 끝난 뒤 호출되어 사용자 생성·프로필 갱신과 refresh token 저장을 함께 처리한다.
 */
@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class LoginPersistenceService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    /**
     * provider와 provider 사용자 ID로 기존 사용자를 찾고, 있으면 프로필을 갱신하며 없으면 생성한다.
     * 사용자 변경과 새 refresh token 해시 저장을 같은 트랜잭션에서 커밋한다.
     * 반환한 토큰 원문은 컨트롤러가 인증 Cookie로 전달한다.
     */
    @Transactional
    public AuthTokenPair login(KakaoUser kakaoUser) {
        Instant now = Instant.now(clock);
        User user = userRepository.findByProviderAndProviderUserId(
                        AuthProvider.KAKAO,
                        kakaoUser.providerUserId()
                )
                .map(existing -> {
                    existing.updateProfile(kakaoUser.nickname(), kakaoUser.profileImageUrl(), now);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.create(
                        idGenerator.generate("user"),
                        AuthProvider.KAKAO,
                        kakaoUser.providerUserId(),
                        kakaoUser.nickname(),
                        kakaoUser.profileImageUrl(),
                        now
                )));

        String refreshToken = authTokenService.issueRefreshToken();
        // 재사용 가능한 refresh token 원문은 DB에 저장하지 않는다.
        refreshTokenRepository.save(RefreshToken.create(
                authTokenService.hashRefreshToken(refreshToken),
                user,
                now,
                now.plus(AuthTokenService.REFRESH_TOKEN_TTL)
        ));

        return new AuthTokenPair(authTokenService.issueAccessToken(user.getId()), refreshToken);
    }
}
