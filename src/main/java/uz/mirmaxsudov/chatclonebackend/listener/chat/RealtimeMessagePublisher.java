package uz.mirmaxsudov.chatclonebackend.listener.chat;

import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.mirmaxsudov.chatclonebackend.event.chat.MessageCreatedEvent;
import uz.mirmaxsudov.chatclonebackend.event.chat.MessageDeletedEvent;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.RealtimeMessageEvent;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMessagePublisher {
    public static final String USER_MESSAGE_QUEUE = "/queue/messages";
    private static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    private static final String MESSAGE_DELETED = "MESSAGE_DELETED";

    private final SimpMessagingTemplate messagingTemplate;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = "/user/queue/messages",
            description = """
                    Server-to-client delivery for created and deleted DM and saved messages.
                    Connect to /ws with an Authorization: Bearer <JWT> STOMP CONNECT header,
                    then subscribe to this private user destination. Messages are created only through 
                    POST /api/v1/chats/{chatId}/messages; STOMP SEND frames are rejected.
                    """,
            payloadType = RealtimeMessageEvent.class,
            message = @AsyncMessage(
                    name = "RealtimeMessageEvent",
                    title = "Chat message event",
                    description = "A committed message creation or deletion delivered to an authenticated participant.",
                    contentType = "application/json"
            )
    ))

    @StompAsyncOperationBinding
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MessageCreatedEvent event) {
        for (UUID recipientId : event.recipientIds()) {
            try {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        USER_MESSAGE_QUEUE,
                        toRealtimeEvent(event, recipientId)
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to deliver message {} to WebSocket user {}",
                        event.messageId(),
                        recipientId,
                        exception
                );
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MessageDeletedEvent event) {
        for (UUID recipientId : event.recipientIds()) {
            try {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        USER_MESSAGE_QUEUE,
                        toRealtimeEvent(event, recipientId)
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to deliver message deletion {} to WebSocket user {}",
                        event.messageId(),
                        recipientId,
                        exception
                );
            }
        }
    }

    private RealtimeMessageEvent toRealtimeEvent(MessageCreatedEvent event, UUID recipientId) {
        MessageResponse message = new MessageResponse(
                event.messageId(),
                event.sequence(),
                event.senderId(),
                event.text(),
                event.createdAt(),
                event.senderId().equals(recipientId),
                event.attachments()
        );

        return new RealtimeMessageEvent(
                MESSAGE_CREATED,
                event.chatId(),
                event.chatType(),
                message
        );
    }

    private RealtimeMessageEvent toRealtimeEvent(MessageDeletedEvent event, UUID recipientId) {
        MessageResponse message = new MessageResponse(
                event.messageId(),
                event.sequence(),
                event.senderId(),
                event.text(),
                event.createdAt(),
                event.senderId().equals(recipientId),
                event.attachments()
        );

        return new RealtimeMessageEvent(
                MESSAGE_DELETED,
                event.chatId(),
                event.chatType(),
                message
        );
    }
}
