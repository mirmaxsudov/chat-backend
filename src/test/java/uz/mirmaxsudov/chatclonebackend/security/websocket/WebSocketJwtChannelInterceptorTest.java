package uz.mirmaxsudov.chatclonebackend.security.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketJwtChannelInterceptorTest {
    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @InjectMocks
    private WebSocketJwtChannelInterceptor interceptor;

    @Test
    void connectAuthenticatesBearerTokenAndUsesJwtSubjectAsPrincipalName() {
        Jwt jwt = jwt();
        JwtAuthenticationToken authentication = authentication(jwt);
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        when(jwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);

        Message<byte[]> message = message(StompCommand.CONNECT, null, "Bearer valid-token", null);
        Message<?> result = interceptor.preSend(message, ignoredChannel());
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        assertThat(resultAccessor.getUser()).isSameAs(authentication);
        assertThat(resultAccessor.getUser().getName()).isEqualTo(jwt.getSubject());
    }

    @Test
    void connectRejectsMissingBearerToken() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, ignoredChannel()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("A Bearer token is required for WebSocket connections");
    }

    @Test
    void connectRejectsInvalidOrExpiredToken() {
        when(jwtDecoder.decode("expired-token"))
                .thenThrow(new BadJwtException("JWT expired"));
        Message<byte[]> message = message(
                StompCommand.CONNECT,
                null,
                "Bearer expired-token",
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(message, ignoredChannel()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid or expired WebSocket access token");
    }

    @Test
    void authenticatedClientCanOnlySubscribeToPrivateMessageQueue() {
        JwtAuthenticationToken authentication = authentication(jwt());
        Message<byte[]> allowed = message(
                StompCommand.SUBSCRIBE,
                WebSocketJwtChannelInterceptor.MESSAGE_QUEUE_DESTINATION,
                null,
                authentication
        );
        Message<byte[]> forbidden = message(
                StompCommand.SUBSCRIBE,
                "/queue/public",
                null,
                authentication
        );

        assertThat(interceptor.preSend(allowed, ignoredChannel())).isSameAs(allowed);
        assertThatThrownBy(() -> interceptor.preSend(forbidden, ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the private message queue can be subscribed to");
    }

    @Test
    void websocketSendFramesAreRejected() {
        JwtAuthenticationToken authentication = authentication(jwt());
        Message<byte[]> message = message(StompCommand.SEND, "/app/chats/1/messages", null, authentication);

        assertThatThrownBy(() -> interceptor.preSend(message, ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket message sending is disabled; use the REST API");
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            String authorization,
            JwtAuthenticationToken authentication
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (authentication != null) {
            accessor.setUser(authentication);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("sub", "11111111-1111-1111-1111-111111111111")
        );
    }

    private JwtAuthenticationToken authentication(Jwt jwt) {
        return new JwtAuthenticationToken(
                jwt,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private org.springframework.messaging.MessageChannel ignoredChannel() {
        return (message, timeout) -> true;
    }
}
