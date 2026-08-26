package com.daon.rewrite.auth.repository;

import com.daon.rewrite.auth.entity.OAuthLoginState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthLoginStateRepository extends JpaRepository<OAuthLoginState, String> {

    long deleteByExpiresAtLessThanEqual(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from OAuthLoginState state where state.stateHash = :stateHash")
    Optional<OAuthLoginState> findByStateHashForUpdate(@Param("stateHash") String stateHash);
}
