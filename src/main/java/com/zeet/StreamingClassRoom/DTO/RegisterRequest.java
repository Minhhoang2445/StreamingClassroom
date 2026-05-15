package com.zeet.StreamingClassRoom.DTO;

import com.zeet.StreamingClassRoom.model.Role;

public record RegisterRequest(String username, String password, String confirmPassword, Role role) {
    
}
