package uz.mirmaxsudov.chatclonebackend.listener.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import uz.mirmaxsudov.chatclonebackend.service.presence.PresenceService;

import java.util.UUID;

@Slf4j
@Component
public class WebSocketSessionListener {
    private final PresenceService presenceService;

    public WebSocketSessionListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = event.getUser() == null ? null : event.getUser().getName();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) {
            log.warn("Ignoring WebSocket connection without authenticated user or session");
            return;
        }

        try {
            presenceService.connected(UUID.fromString(user), sessionId);
            log.debug("WebSocket connected: user={}, session={}", user, sessionId);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring WebSocket connection with invalid user id: user={}", user);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = event.getUser() == null ? "unknown" : event.getUser().getName();
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            presenceService.disconnected(sessionId);
        }
        log.debug(
                "WebSocket disconnected: user={}, session={}, status={}",
                user,
                sessionId,
                event.getCloseStatus()
        );
    }
}
