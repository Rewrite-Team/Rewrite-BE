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

    // 별도 정리 작업 없이 새 로그인 시작 시 만료된 state 기록을 제거한다.
    long deleteByExpiresAtLessThanEqual(Instant now);

    /**
     * 동일 state를 소비하는 callback들이 검증·삭제를 동시에 수행하지 못하도록 행을 잠근다.
     * 호출 서비스는 조회부터 삭제까지 같은 트랜잭션으로 묶어 state의 재사용을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from OAuthLoginState state where state.stateHash = :stateHash")
    Optional<OAuthLoginState> findByStateHashForUpdate(@Param("stateHash") String stateHash);
}
