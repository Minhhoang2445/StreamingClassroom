package com.zeet.StreamingClassRoom.DTO;

public record AuthResponse( 
        String accessToken,
        String refreshToken,
        String tokenType
) {

}
