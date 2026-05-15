package com.zeet.StreamingClassRoom.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;

@Service
public class StreamService {

    @Value("${livekit.api.key}")
    private String LIVEKIT_API_KEY; 

    @Value("${livekit.api.secret}")
    private String LIVEKIT_API_SECRET; 

    public String generateToken(String roomName, String identity, boolean isTeacher) {
        AccessToken token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET); 
        token.setName(identity); 
        token.setIdentity(identity);
        
        if (isTeacher) {
            token.addGrants(new RoomJoin(true), new RoomName(roomName), new RoomAdmin(true)); 
        } else {
            token.addGrants(new RoomJoin(true), new RoomName(roomName)); 
        }
        return token.toJwt();
    }

     
}
