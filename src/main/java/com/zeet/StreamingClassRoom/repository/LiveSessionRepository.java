package com.zeet.StreamingClassRoom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.LiveSession;
import com.zeet.StreamingClassRoom.model.LiveSessionStatus;

public interface LiveSessionRepository extends JpaRepository<LiveSession, String> {

    List<LiveSession> findByClassroomIdOrderByScheduledStartTimeDesc(String classroomId);

    Optional<LiveSession> findById(String id);

    Optional<LiveSession> findByLivekitRoomName(String livekitRoomName);

    boolean existsByLivekitRoomName(String livekitRoomName);

    List<LiveSession> findByClassroomIdAndStatusOrderByScheduledStartTimeDesc(
            String classroomId,
            LiveSessionStatus status);
    
    
}
