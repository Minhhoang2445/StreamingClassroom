package com.zeet.StreamingClassRoom.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeet.StreamingClassRoom.DTO.CreateLiveSessionRequest;
import com.zeet.StreamingClassRoom.DTO.LiveSessionResponse;
import com.zeet.StreamingClassRoom.DTO.LiveSessionTokenResponse;
import com.zeet.StreamingClassRoom.DTO.UpdateLiveSessionRequest;
import com.zeet.StreamingClassRoom.service.LiveSessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @PostMapping("/api/classrooms/{classroomId}/sessions")
    @PreAuthorize("hasRole('ADMIN') or @classroomSecurity.canManageClassroom(#classroomId, authentication)")
    public ResponseEntity<LiveSessionResponse> createSession(
            @PathVariable String classroomId,
            @Valid @RequestBody CreateLiveSessionRequest request,
            Authentication authentication) {
        LiveSessionResponse response = liveSessionService.createSession(classroomId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/classrooms/{classroomId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#classroomId, authentication)")
    public ResponseEntity<List<LiveSessionResponse>> getSessionsByClassroom(
            @PathVariable String classroomId,
            Authentication authentication) {
        return ResponseEntity.ok(liveSessionService.getSessionsByClassroom(classroomId, authentication));
    }

    @GetMapping("/api/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<LiveSessionResponse> getSessionById(
            @PathVariable String sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(liveSessionService.getSessionById(sessionId, authentication));
    }

    @PutMapping("/api/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<LiveSessionResponse> updateSession(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateLiveSessionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(liveSessionService.updateSession(sessionId, request, authentication));
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        liveSessionService.deleteSession(sessionId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/sessions/{sessionId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<LiveSessionTokenResponse> startSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(liveSessionService.startSession(sessionId, authentication));
    }
    @PostMapping("/api/sessions/{sessionId}/join")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TEACHER')")
    public ResponseEntity<LiveSessionTokenResponse> joinSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(liveSessionService.joinSession(sessionId, authentication));
    }


}
