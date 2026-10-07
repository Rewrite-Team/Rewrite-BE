package com.daon.rewrite.auth.repository;

import com.daon.rewrite.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * 같은 refresh token의 갱신·로그아웃이 활성 여부 확인과 폐기를 동시에 수행하지 못하도록 행을 잠근다.
     * 호출 서비스가 잠금 안에서 상태를 확인하므로 앞선 요청에서 폐기한 토큰은 다시 사용할 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
