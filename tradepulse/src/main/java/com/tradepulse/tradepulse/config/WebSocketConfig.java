package com.tradepulse.tradepulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final com.tradepulse.tradepulse.auth.service.JwtService jwt;
    private final org.springframework.security.core.userdetails.UserDetailsService users;
    private final String[] origins;
    public WebSocketConfig(com.tradepulse.tradepulse.auth.service.JwtService jwt, org.springframework.security.core.userdetails.UserDetailsService users,
        @org.springframework.beans.factory.annotation.Value("${WEB_ALLOWED_ORIGINS:http://localhost:4200,http://127.0.0.1:4200,http://localhost:8080,http://127.0.0.1:8080}") String[] origins) { this.jwt=jwt; this.users=users; this.origins=origins; }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, org.springframework.messaging.MessageChannel channel) {
                var a=org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(message, org.springframework.messaging.simp.stomp.StompHeaderAccessor.class);
                if(a==null) return message;
                if(a.getCommand()==org.springframework.messaging.simp.stomp.StompCommand.CONNECT) {
                    String header=a.getFirstNativeHeader("Authorization");
                    if(header==null || !header.startsWith("Bearer ")) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                    var user=users.loadUserByUsername(jwt.extractUsername(header.substring(7)));
                    if(!jwt.isTokenValid(header.substring(7),user))throw new org.springframework.security.access.AccessDeniedException("Invalid token");
                    a.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
                }
                if(a.getCommand()==org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE) {
                    String dest=a.getDestination();
                    if(a.getUser()==null || dest==null || !(dest.matches("/topic/market/[A-Za-z0-9._-]+") || dest.equals("/user/queue/notifications") || dest.equals("/user/queue/backtests"))) throw new org.springframework.security.access.AccessDeniedException("Subscription denied");
                }
                if(a.getCommand()==org.springframework.messaging.simp.stomp.StompCommand.SEND)throw new org.springframework.security.access.AccessDeniedException("Read-only feed");
                return message;
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-native").setAllowedOrigins(origins);
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
    }
}
