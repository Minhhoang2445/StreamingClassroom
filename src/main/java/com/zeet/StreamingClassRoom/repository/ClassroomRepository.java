package com.zeet.StreamingClassRoom.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.Classroom;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {

    Optional<Classroom> findByClassCode(String classCode);

    boolean existsByClassCode(String classCode);

    boolean existsByClassCodeAndIdNot(String classCode, String id);
}
