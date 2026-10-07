package com.daon.rewrite.auth;

import com.daon.rewrite.auth.entity.User;
import com.daon.rewrite.auth.repository.UserRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Security 필터에서 검증한 JWT의 사용자 ID로 DB의 현재 프로필을 조회한다.
 * 인증 정보가 없거나 해당 사용자가 DB에 없으면 도메인 처리 전에 인증 실패로 종료한다.
 */
@Component
@Profile("auth-real")
@RequiredArgsConstructor
public class AuthenticatedCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    @Override
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // AuthTokenService가 JWT의 sub에 넣은 사용자 ID를 JwtAuthenticationToken의 name으로 읽는다.
        User user = userRepository.findById(authentication.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new CurrentUser(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.getCreatedAt()
        );
    }
}
