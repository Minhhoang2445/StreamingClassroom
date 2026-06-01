package com.zeet.StreamingClassRoom.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function; 

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JWTService {
    
    @Value("${jwt.secret.key}")
    private String secretKey;
    
    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        return Jwts.builder()
                .claims(claims) 
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 30)) 
                .signWith(getKey())
                .compact();
    }

    
    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUsernameFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String getRoleFromToken(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class)); 
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey()) // Xác thực chữ ký bằng SecretKey
                .build()
                .parseSignedClaims(token) // Đọc token
                .getPayload(); // Lấy nội dung Claims ra
    }

    // Hàm kiểm tra tính hợp lệ (Còn hạn không? Chữ ký đúng không?)
    public boolean isAccessTokenValid(String token){
        try {
            // Chỉ cần hàm parse chạy thành công mà không văng Exception tức là token xịn
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false; 
        }
    }

    public String extractUsername(String jwt) {
        return extractClaim(jwt, Claims::getSubject);
    }

    
}