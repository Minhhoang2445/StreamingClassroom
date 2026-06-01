package com.zeet.StreamingClassRoom.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserId(String userId);
}
