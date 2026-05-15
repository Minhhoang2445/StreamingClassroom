package com.zeet.StreamingClassRoom.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
        var newUser = new User();
        newUser.setUsername(user.username());
        newUser.setPasswordHash(encoder.encode(user.password()));
        newUser.setRole(user.role());
        authRepository.save(newUser);
    }

    public String login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.username(), 
                loginRequest.password()
            )
        );
            var userDetails = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
            String userName = userDetails.getUsername();
            String role = userDetails.getAuthorities().iterator().next().getAuthority();
            return jwtService.generateAccessToken(userName, role);
        
    }

    public String refreshAccessToken(String refreshToken) {
            return refreshTokenService.findByToken(refreshToken)
            .map(refreshTokenService::verifyExpiration)
            .map(RefreshToken::getUser)
            .map(user -> {
                String role = user.getRole().name(); 
                return jwtService.generateAccessToken(user.getUsername(), "ROLE_" + role);
            })
            .orElseThrow(() -> new RuntimeException("Refresh token không tồn tại trong hệ thống!"));

    }
}