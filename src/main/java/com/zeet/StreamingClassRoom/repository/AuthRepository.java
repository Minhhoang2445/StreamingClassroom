package com.zeet.StreamingClassRoom.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zeet.StreamingClassRoom.model.User;

public interface AuthRepository extends JpaRepository<User, String> {
    User findByUsername(String username);
}
