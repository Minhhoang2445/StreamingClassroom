package com.zeet.StreamingClassRoom.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeet.StreamingClassRoom.DTO.ClassroomMemberResponse;
import com.zeet.StreamingClassRoom.DTO.ClassroomResponse;
import com.zeet.StreamingClassRoom.DTO.CreateClassroomRequest;
import com.zeet.StreamingClassRoom.DTO.JoinClassroomRequest;
import com.zeet.StreamingClassRoom.DTO.UpdateClassroomRequest;
import com.zeet.StreamingClassRoom.exception.BadRequestException;
import com.zeet.StreamingClassRoom.exception.ForbiddenException;
import com.zeet.StreamingClassRoom.exception.ResourceNotFoundException;
import com.zeet.StreamingClassRoom.model.Classroom;
import com.zeet.StreamingClassRoom.model.ClassroomMember;
import com.zeet.StreamingClassRoom.model.ClassroomMemberRole;
import com.zeet.StreamingClassRoom.model.ClassroomMemberStatus;
import com.zeet.StreamingClassRoom.model.Role;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.ClassroomMemberRepository;
import com.zeet.StreamingClassRoom.repository.ClassroomRepository;
import com.zeet.StreamingClassRoom.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final UserRepository userRepository;

    public ClassroomResponse createClassroom(CreateClassroomRequest request) {
        User teacher = getValidTeacher(request.teacherId());
        ensureClassCodeAvailable(request.classCode());

        Classroom classroom = new Classroom();
        classroom.setName(request.name());
        classroom.setDescription(request.description());
        classroom.setTeacher(teacher);
        classroom.setClassCode(request.classCode());

        Classroom savedClassroom = classroomRepository.save(classroom);
        ensureTeacherMembership(savedClassroom, teacher);

        return toClassroomResponse(savedClassroom);
    }

    @Transactional(readOnly = true)
    public List<ClassroomResponse> getClassroomsForCurrentUser(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.TEACHER) {
            return classroomRepository.findAll()
                    .stream()
                    .map(this::toClassroomResponse)
                    .toList();
        }

        return classroomMemberRepository.findByUserIdAndStatus(currentUser.getId(), ClassroomMemberStatus.ACTIVE)
                .stream()
                .map(ClassroomMember::getClassroom)
                .map(this::toClassroomResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassroomResponse getClassroomById(String id, Authentication authentication) {
        Classroom classroom = getClassroomOrThrow(id);
        ensureCurrentUserCanAccessClassroom(classroom.getId(), authentication);
        return toClassroomResponse(classroom);
    }

    public ClassroomResponse updateClassroom(String id, UpdateClassroomRequest request) {
        Classroom classroom = getClassroomOrThrow(id);

        if (hasText(request.name())) {
            classroom.setName(request.name());
        }

        if (request.description() != null) {
            classroom.setDescription(request.description());
        }

        if (hasText(request.classCode()) && !request.classCode().equals(classroom.getClassCode())) {
            ensureClassCodeAvailableForUpdate(request.classCode(), id);
            classroom.setClassCode(request.classCode());
        }

        if (hasText(request.teacherId()) && !request.teacherId().equals(classroom.getTeacher().getId())) {
            User teacher = getValidTeacher(request.teacherId());
            classroom.setTeacher(teacher);
            ensureTeacherMembership(classroom, teacher);
        }

        return toClassroomResponse(classroomRepository.save(classroom));
    }

    public void deleteClassroom(String id) {
        Classroom classroom = getClassroomOrThrow(id);
        classroomMemberRepository.deleteByClassroomId(classroom.getId());
        classroomRepository.delete(classroom);
    }

    public ClassroomMemberResponse joinClassroom(JoinClassroomRequest request, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        if (currentUser.getRole() != Role.STUDENT) {
            throw new ForbiddenException("Only students can join classrooms");
        }

        Classroom classroom = classroomRepository.findByClassCode(request.classCode())
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        ClassroomMember member = classroomMemberRepository.findByClassroomIdAndUserId(
                        classroom.getId(),
                        currentUser.getId())
                .map(existingMember -> reactivateMembershipIfAllowed(existingMember, classroom))
                .orElseGet(() -> createStudentMembership(classroom, currentUser));

        return toClassroomMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public List<ClassroomMemberResponse> getClassroomMembers(String classroomId, Authentication authentication) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCurrentUserCanAccessClassroom(classroom.getId(), authentication);

        return classroomMemberRepository.findByClassroomId(classroom.getId())
                .stream()
                .map(this::toClassroomMemberResponse)
                .toList();
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Authentication is required");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private Classroom getClassroomOrThrow(String id) {
        return classroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private User getValidTeacher(String teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new BadRequestException("Teacher must have TEACHER or ADMIN role");
        }

        return teacher;
    }

    private void ensureClassCodeAvailable(String classCode) {
        if (classroomRepository.existsByClassCode(classCode)) {
            throw new BadRequestException("Class code already exists");
        }
    }

    private void ensureClassCodeAvailableForUpdate(String classCode, String classroomId) {
        if (classroomRepository.existsByClassCodeAndIdNot(classCode, classroomId)) {
            throw new BadRequestException("Class code already exists");
        }
    }

    private void ensureTeacherMembership(Classroom classroom, User teacher) {
        ClassroomMember member = classroomMemberRepository.findByClassroomIdAndUserId(
                        classroom.getId(),
                        teacher.getId())
                .orElseGet(() -> {
                    ClassroomMember newMember = new ClassroomMember();
                    newMember.setClassroom(classroom);
                    newMember.setUser(teacher);
                    newMember.setJoinedAt(LocalDateTime.now());
                    return newMember;
                });

        member.setRole(ClassroomMemberRole.TEACHER);
        member.setStatus(ClassroomMemberStatus.ACTIVE);
        classroomMemberRepository.save(member);
    }

    private ClassroomMember createStudentMembership(Classroom classroom, User user) {
        ClassroomMember member = new ClassroomMember();
        member.setClassroom(classroom);
        member.setUser(user);
        member.setRole(ClassroomMemberRole.STUDENT);
        member.setStatus(ClassroomMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        return classroomMemberRepository.save(member);
    }

    private ClassroomMember reactivateMembershipIfAllowed(ClassroomMember member, Classroom classroom) {
        if (member.getStatus() == ClassroomMemberStatus.BLOCKED) {
            throw new ForbiddenException("You are blocked from this classroom");
        }

        if (member.getStatus() != ClassroomMemberStatus.ACTIVE) {
            member.setStatus(ClassroomMemberStatus.ACTIVE);
            member.setRole(ClassroomMemberRole.STUDENT);
            member.setJoinedAt(LocalDateTime.now());
            return classroomMemberRepository.save(member);
        }

        return member;
    }

    private void ensureCurrentUserCanAccessClassroom(String classroomId, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.TEACHER) {
            return;
        }

        boolean activeMember = classroomMemberRepository.existsByClassroomIdAndUserIdAndStatus(
                classroomId,
                currentUser.getId(),
                ClassroomMemberStatus.ACTIVE);

        if (!activeMember) {
            throw new ForbiddenException("You do not have access to this classroom");
        }
    }

    private ClassroomResponse toClassroomResponse(Classroom classroom) {
        long memberCount = classroomMemberRepository.countByClassroomIdAndStatus(
                classroom.getId(),
                ClassroomMemberStatus.ACTIVE);

        return new ClassroomResponse(
                classroom.getId(),
                classroom.getName(),
                classroom.getDescription(),
                classroom.getClassCode(),
                classroom.getTeacher().getId(),
                classroom.getTeacher().getUsername(),
                classroom.getCreatedAt(),
                classroom.getUpdatedAt(),
                memberCount);
    }

    private ClassroomMemberResponse toClassroomMemberResponse(ClassroomMember member) {
        return new ClassroomMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getClassroom().getId(),
                member.getClassroom().getName(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
