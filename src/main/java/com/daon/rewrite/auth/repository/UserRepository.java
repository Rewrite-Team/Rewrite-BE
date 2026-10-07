package com.daon.rewrite.auth.repository;

import com.daon.rewrite.auth.AuthProvider;
import com.daon.rewrite.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    // 외부 사용자 ID는 provider 범위에서 식별해 재로그인 시 기존 Rewrite 계정을 재사용한다.
    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
