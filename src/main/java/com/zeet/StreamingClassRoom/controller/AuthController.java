package com.zeet.StreamingClassRoom.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zeet.StreamingClassRoom.DTO.AuthResponse;
import com.zeet.StreamingClassRoom.DTO.LoginRequest;
import com.zeet.StreamingClassRoom.DTO.RefreshTokenRequest;
import com.zeet.StreamingClassRoom.DTO.RegisterRequest;
import com.zeet.StreamingClassRoom.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest registerRequest
    ) {
        if (!registerRequest.password().equals(registerRequest.confirmPassword())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Passwords do not match");
        }

        authService.register(registerRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Registration successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(
                authService.refreshToken(request.refreshToken())
        );
    }
}