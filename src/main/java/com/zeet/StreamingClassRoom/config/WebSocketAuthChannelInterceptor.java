package com.zeet.StreamingClassRoom.config;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.zeet.StreamingClassRoom.service.JWTService;
import com.zeet.StreamingClassRoom.service.MyUserDetailsService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JWTService jwtService;
    private final MyUserDetailsService myUserDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (!StompCommand.CONNECT.equals(command)) {
            return message;
        }

        System.out.println("=== WEBSOCKET CONNECT ===");

        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        System.out.println("Authorization headers = " + authorizationHeaders);

        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            System.out.println("No Authorization header");
            return message;
        }

        String authorizationHeader = authorizationHeaders.get(0);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            System.out.println("Invalid Authorization header");
            return message;
        }

        String jwt = authorizationHeader.substring("Bearer ".length()).trim();

        System.out.println("JWT only = " + jwt);

        try {
            if (!jwtService.isAccessTokenValid(jwt)) {
                System.out.println("Invalid JWT");
                return message;
            }

            String username = jwtService.extractUsername(jwt);
            System.out.println("JWT username = " + username);

            if (username == null || username.isBlank()) {
                System.out.println("Username is null or blank");
                return message;
            }

            UserDetails userDetails = myUserDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            accessor.setUser(authentication);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            System.out.println("WebSocket authenticated user = " + authentication.getName());
            System.out.println("Accessor user = " + accessor.getUser());

            return message;

        } catch (Exception ex) {
            System.out.println("WebSocket authentication error: " + ex.getMessage());
            ex.printStackTrace();
            return message;
        }
    }
}