package com.zeet.StreamingClassRoom.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeet.StreamingClassRoom.service.StreamService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {
    
    private final StreamService streamService;
    @PostMapping("/token")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    public ResponseEntity<Map<String, String>> generateToken(@RequestBody Map<String, String> request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();


        String roomName = request.get("roomName");
        String identity = request.get("identity");
        String role = authentication.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("");
        if (roomName == null || identity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing roomName or identity"));
        }
        String token = streamService.generateToken(roomName, identity, role.equals("ROLE_TEACHER"));
        return ResponseEntity.ok(Map.of("token", token));
        }
    
}
