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
@Profile("!local & !test")
@RequiredArgsConstructor
public class LoginPersistenceService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    // 카카오 사용자 정보를 기준으로 Rewrite 사용자를 조회하거나 생성한 뒤 access token 과 refresh token 을 발급하는 로그인 비즈니스 로직
    @Transactional
    public AuthTokenPair login(KakaoUser kakaoUser) {
        Instant now = Instant.now(clock);
        // 기존 사용자 조회
        User user = userRepository.findByProviderAndProviderUserId(
                        AuthProvider.KAKAO,
                        kakaoUser.providerUserId()
                )
                // 기존 사용자가 존재하는 경우
                // 신규 사용자를 만들지 않고 프로필 정보를 최신 카카오 정보로 갱신
                // repository 조회로 가져온 JPA Entity 사용자이다. 즉 현재 트랜잭현 안에서 영속성 컨텍스트가 관리하는 managed entity 이다.
                // 따라서 userRepository.save(existing) 코드 없어도 트랜잭션이 commit 되는 시점에 JPA의 dirty checking이 변경을 감지하여 필요한 UPDATE SQL을 자동으로 실행
                .map(existing -> {
                    existing.updateProfile(kakaoUser.nickname(), kakaoUser.profileImageUrl(), now);
                    return existing;
                })
                // 기존 사용자가 없는 경우
                // 신규 사용자를 생성하여 DB에 저장. 저장된 Entity 반환
                .orElseGet(() -> userRepository.save(User.create(
                        idGenerator.generate("user"),
                        AuthProvider.KAKAO,
                        kakaoUser.providerUserId(),
                        kakaoUser.nickname(),
                        kakaoUser.profileImageUrl(),
                        now
                )));

        // refresh token 원문 생성 생성
        String refreshToken = authTokenService.issueRefreshToken();
        refreshTokenRepository.save(RefreshToken.create(
                authTokenService.hashRefreshToken(refreshToken),    // refresh token 원문을 해시하여 DB에 저장
                user,
                now,
                now.plus(AuthTokenService.REFRESH_TOKEN_TTL)
        ));

        // access token 과 refresh token 을 담은 AuthTokenPair 반환
        return new AuthTokenPair(authTokenService.issueAccessToken(user.getId()), refreshToken);
    }
}
