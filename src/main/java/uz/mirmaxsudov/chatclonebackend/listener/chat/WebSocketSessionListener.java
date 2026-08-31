package uz.mirmaxsudov.chatclonebackend.listener.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketSessionListener {
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String user = event.getUser() == null ? "unknown" : event.getUser().getName();
        log.debug("WebSocket connected: user={}", user);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = event.getUser() == null ? "unknown" : event.getUser().getName();
        log.debug(
                "WebSocket disconnected: user={}, session={}, status={}",
                user,
                accessor.getSessionId(),
                event.getCloseStatus()
        );
    }
}
