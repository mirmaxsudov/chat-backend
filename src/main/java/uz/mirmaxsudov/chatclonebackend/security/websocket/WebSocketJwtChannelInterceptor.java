package uz.mirmaxsudov.chatclonebackend.security.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {
    public static final String MESSAGE_QUEUE_DESTINATION = "/user/queue/messages";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null)
            return message;

        StompCommand command = accessor.getCommand();

        if (command == null)
            return message;

        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        if (command == StompCommand.DISCONNECT)
            return message;

        requireAuthenticated(accessor.getUser());

        if (accessor.getMessageType() == SimpMessageType.MESSAGE || command == StompCommand.SEND) {
            throw new AccessDeniedException("WebSocket message sending is disabled; use the REST API");
        }

        if (command == StompCommand.SUBSCRIBE
                && !MESSAGE_QUEUE_DESTINATION.equals(accessor.getDestination())) {
            throw new AccessDeniedException("Only the private message queue can be subscribed to");
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);

        String authorization = authorizationHeaders == null || authorizationHeaders.isEmpty()
                ? null
                : authorizationHeaders.getFirst();

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX))
            throw new BadCredentialsException("A Bearer token is required for WebSocket connections");

        String token = authorization.substring(BEARER_PREFIX.length()).strip();

        if (token.isEmpty())
            throw new BadCredentialsException("A Bearer token is required for WebSocket connections");


        try {
            Authentication authentication = jwtAuthenticationConverter.convert(jwtDecoder.decode(token));
            if (authentication == null)
                throw new BadCredentialsException("Invalid WebSocket access token");
            return authentication;
        } catch (JwtException exception) {
            throw new BadCredentialsException("Invalid or expired WebSocket access token", exception);
        }
    }

    private void requireAuthenticated(java.security.Principal principal) {
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket authentication is required");
        }
    }
}
