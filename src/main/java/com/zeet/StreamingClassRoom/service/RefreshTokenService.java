package com.zeet.StreamingClassRoom.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.model.RefreshToken;
import com.zeet.StreamingClassRoom.repository.AuthRepository;
import com.zeet.StreamingClassRoom.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthRepository authRepository;

    public RefreshToken createRefreshToken(String username) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(authRepository.findByUsername(username)) // Tìm user để liên kết khóa ngoại
                .token(UUID.randomUUID().toString()) // Tạo chuỗi ngẫu nhiên
                .expiryDate(Instant.now().plusMillis(1000 * 60 * 60 * 24 * 7)) // Hạn 7 ngày
                .revoked(false)
                .build();
                
        return refreshTokenRepository.save(refreshToken);
    }

    // 2. Tìm Token trong DB
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    // 3. Kiểm tra hạn sử dụng (Dựa vào trường expiryDate trong DB)
    public RefreshToken verifyExpiration(RefreshToken token) {
        // Nếu thời gian hiện tại đã vượt qua ngày hết hạn
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token); // Xóa luôn token rác khỏi DB
            throw new RuntimeException("Refresh token đã hết hạn. Vui lòng đăng nhập lại!");
        }
        // Nếu bị thu hồi
        if (token.isRevoked()) {
            throw new RuntimeException("Refresh token đã bị thu hồi!");
        }
        return token;
    }
}