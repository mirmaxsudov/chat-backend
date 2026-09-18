package uz.mirmaxsudov.chatclonebackend.listener.chat;

import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.RealtimePresenceEvent;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.UserPresenceResponse;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePresencePublisher {
    public static final String USER_PRESENCE_QUEUE = "/queue/presence";
    private static final String PRESENCE_CHANGED = "PRESENCE_CHANGED";

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMemberRepository chatMemberRepository;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/user/queue/presence",
            description = "Private online/offline changes for the authenticated user's direct-chat peers.",
            payloadType = RealtimePresenceEvent.class,
            message = @AsyncMessage(
                    name = "RealtimePresenceEvent",
                    title = "Presence changed",
                    description = "A direct-chat peer changed between online and offline.",
                    contentType = "application/json"
            )
    ))

    @StompAsyncOperationBinding
    public void publish(UserPresenceResponse presence) {
        RealtimePresenceEvent event = new RealtimePresenceEvent(
                PRESENCE_CHANGED,
                presence.userId(),
                presence.status(),
                presence.lastSeenAt(),
                presence.changedAt()
        );

        List<UUID> recipientIds;
        try {
            recipientIds = chatMemberRepository.findActiveDirectPeerIds(presence.userId());
        } catch (DataAccessException exception) {
            log.error("Failed to load presence recipients for user {}", presence.userId(), exception);
            return;
        }

        for (UUID recipientId : recipientIds) {
            try {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        USER_PRESENCE_QUEUE,
                        event
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to deliver presence for user {} to WebSocket user {}",
                        presence.userId(),
                        recipientId,
                        exception
                );
            }
        }
    }
}
