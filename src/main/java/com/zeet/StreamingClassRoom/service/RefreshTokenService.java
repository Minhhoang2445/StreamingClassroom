package com.zeet.StreamingClassRoom.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.exception.ForbiddenException;
import com.zeet.StreamingClassRoom.model.RefreshToken;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.AuthRepository;
import com.zeet.StreamingClassRoom.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final long REFRESH_TOKEN_DAYS = 7;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthRepository authRepository;

    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setRevoked(false);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));

        return refreshTokenRepository.save(refreshToken);
    }

    // 2. Tìm Token trong DB
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    // 3. Kiểm tra hạn sử dụng (Dựa vào trường expiryDate trong DB)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ForbiddenException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new ForbiddenException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ForbiddenException("Refresh token has expired");
        }

        return refreshToken;
    }
    public void revokeToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        }
    }
   
}