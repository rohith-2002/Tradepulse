package com.tradepulse.tradepulse.auth.repository;

import com.tradepulse.tradepulse.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    void deleteByUserId(Long userId);
}
