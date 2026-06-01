package com.zeet.StreamingClassRoom.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.zeet.StreamingClassRoom.DTO.AuthResponse;
import com.zeet.StreamingClassRoom.DTO.LoginRequest;
import com.zeet.StreamingClassRoom.DTO.RegisterRequest;
import com.zeet.StreamingClassRoom.model.RefreshToken;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.AuthRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder encoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public void register(RegisterRequest user) {
        if (authRepository.findByUsername(user.username()) != null) {
            throw new RuntimeException("Username already exists");
        }

        User newUser = new User();
        newUser.setUsername(user.username());
        newUser.setPasswordHash(encoder.encode(user.password()));
        newUser.setRole(user.role());

        authRepository.save(newUser);
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.username(),
                        loginRequest.password()
                )
        );

        var userDetails =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

        String username = userDetails.getUsername();
        String role = userDetails.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        User user = authRepository.findByUsername(username);

        if (user == null) {
            throw new RuntimeException("User not found");
        }

        String accessToken = jwtService.generateAccessToken(username, role);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer"
        );
    }
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken oldRefreshToken =
                refreshTokenService.validateRefreshToken(refreshTokenValue);

        User user = oldRefreshToken.getUser();

        String role = "ROLE_" + user.getRole().name();

        String newAccessToken =
                jwtService.generateAccessToken(user.getUsername(), role);

        refreshTokenService.revokeToken(oldRefreshToken.getToken());

        RefreshToken newRefreshToken =
                refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken.getToken(),
                "Bearer"
        );
    }
}