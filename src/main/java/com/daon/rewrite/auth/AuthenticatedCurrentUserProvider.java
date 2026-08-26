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

@Component
@Profile("!local & !test")  // 개발 로컬이나 테스트에서는 CurrentUserProvider 구현제를 사용
@RequiredArgsConstructor
public class AuthenticatedCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    @Override
    public CurrentUser currentUser() {
        // Spring Security가 저장해 둔 현재 사용자의 인증 정보를 가져옴
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 인증 객체가 없거나 인증되지 않은 객체인 경우 HTTP 401 Unauthorized응답으로 변환
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Spring Security 의 기본 JwtAuthenticationToken 은 JWT의 sub를 이름으로 사용
        // 토큰 발습 시 .subject(userId) 를 넣었으므로 authentication.getName() = JWT의 sub = userId
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
