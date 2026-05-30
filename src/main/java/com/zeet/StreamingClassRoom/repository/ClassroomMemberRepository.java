package com.zeet.StreamingClassRoom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.ClassroomMember;
import com.zeet.StreamingClassRoom.model.ClassroomMemberRole;
import com.zeet.StreamingClassRoom.model.ClassroomMemberStatus;

public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, String> {

    boolean existsByClassroomIdAndUserIdAndStatus(String classroomId, String userId, ClassroomMemberStatus status);

    boolean existsByClassroomIdAndUserIdAndStatusAndRole(
            String classroomId,
            String userId,
            ClassroomMemberStatus status,
            ClassroomMemberRole role);

    Optional<ClassroomMember> findByClassroomIdAndUserId(String classroomId, String userId);

    List<ClassroomMember> findByClassroomId(String classroomId);

    List<ClassroomMember> findByUserIdAndStatus(String userId, ClassroomMemberStatus status);

    long countByClassroomIdAndStatus(String classroomId, ClassroomMemberStatus status);

    void deleteByClassroomId(String classroomId);
}
