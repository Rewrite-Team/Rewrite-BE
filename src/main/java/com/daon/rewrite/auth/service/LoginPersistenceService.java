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

@Service
@Profile("auth-real")
@RequiredArgsConstructor
public class LoginPersistenceService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    // 카카오 조회가 끝난 뒤 사용자 변경과 refresh token 저장을 같은 트랜잭션에서 처리한다.
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
