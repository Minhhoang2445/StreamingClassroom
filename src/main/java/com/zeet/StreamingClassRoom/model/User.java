package com.zeet.StreamingClassRoom.model;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    
    @Column(name = "username", nullable = false, unique = true) 
    private String username;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash; 
    
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role; // "STUDENT" hoặc "TEACHER" HOAJR "ADMIN"

    @CreationTimestamp // Tự động lấy giờ hệ thống khi tạo mới record
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; 
}