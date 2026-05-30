package com.zeet.StreamingClassRoom.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.zeet.StreamingClassRoom.model.Classroom;
import com.zeet.StreamingClassRoom.model.ClassroomMemberRole;
import com.zeet.StreamingClassRoom.model.ClassroomMemberStatus;
import com.zeet.StreamingClassRoom.model.Role;
import com.zeet.StreamingClassRoom.model.User;
import com.zeet.StreamingClassRoom.repository.ClassroomMemberRepository;
import com.zeet.StreamingClassRoom.repository.ClassroomRepository;
import com.zeet.StreamingClassRoom.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component("classroomSecurity")
@RequiredArgsConstructor
public class ClassroomSecurity {

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;

    public boolean isActiveMember(String classroomId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> canAccessClassroom(classroomId, user))
                .orElse(false);
    }

    public boolean canManageClassroom(String classroomId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> canManageClassroom(classroomId, user))
                .orElse(false);
    }

    private boolean canAccessClassroom(String classroomId, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER) {
            return true;
        }

        return classroomMemberRepository.existsByClassroomIdAndUserIdAndStatus(
                classroomId,
                user.getId(),
                ClassroomMemberStatus.ACTIVE);
    }

    private boolean canManageClassroom(String classroomId, User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        if (user.getRole() != Role.TEACHER) {
            return false;
        }

        return classroomRepository.findById(classroomId)
                .map(classroom -> isClassroomTeacherOrTeacherMember(classroom, user))
                .orElse(false);
    }

    private boolean isClassroomTeacherOrTeacherMember(Classroom classroom, User user) {
        if (classroom.getTeacher().getId().equals(user.getId())) {
            return true;
        }

        return classroomMemberRepository.existsByClassroomIdAndUserIdAndStatusAndRole(
                classroom.getId(),
                user.getId(),
                ClassroomMemberStatus.ACTIVE,
                ClassroomMemberRole.TEACHER);
    }
}
