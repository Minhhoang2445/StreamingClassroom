package com.zeet.StreamingClassRoom.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeet.StreamingClassRoom.DTO.ClassroomMemberResponse;
import com.zeet.StreamingClassRoom.DTO.ClassroomResponse;
import com.zeet.StreamingClassRoom.DTO.CreateClassroomRequest;
import com.zeet.StreamingClassRoom.DTO.JoinClassroomRequest;
import com.zeet.StreamingClassRoom.DTO.UpdateClassroomRequest;
import com.zeet.StreamingClassRoom.service.ClassroomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassroomResponse> createClassroom(@Valid @RequestBody CreateClassroomRequest request) {
        ClassroomResponse response = classroomService.createClassroom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<List<ClassroomResponse>> getClassrooms(Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomsForCurrentUser(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#id, authentication)")
    public ResponseEntity<ClassroomResponse> getClassroomById(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassroomResponse> updateClassroom(
            @PathVariable String id,
            @Valid @RequestBody UpdateClassroomRequest request) {
        return ResponseEntity.ok(classroomService.updateClassroom(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClassroom(@PathVariable String id) {
        classroomService.deleteClassroom(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClassroomMemberResponse> joinClassroom(
            @Valid @RequestBody JoinClassroomRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(classroomService.joinClassroom(request, authentication));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#id, authentication)")
    public ResponseEntity<List<ClassroomMemberResponse>> getClassroomMembers(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomMembers(id, authentication));
    }
}
