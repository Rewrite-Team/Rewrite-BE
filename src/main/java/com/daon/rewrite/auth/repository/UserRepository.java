package com.daon.rewrite.auth.repository;

import com.daon.rewrite.auth.AuthProvider;
import com.daon.rewrite.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
