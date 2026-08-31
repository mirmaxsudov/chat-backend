package uz.mirmaxsudov.chatclonebackend.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uz.mirmaxsudov.chatclonebackend.event.chat.MessageCreatedEvent;
import uz.mirmaxsudov.chatclonebackend.listener.chat.RealtimeMessagePublisher;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.RealtimeMessageEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimeMessagePublisherTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RealtimeMessagePublisher publisher;

    @Test
    void directMessageIsMappedForBothPrivateUserQueues() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        MessageCreatedEvent event = event(ChatType.DIRECT, senderId, List.of(senderId, recipientId));
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.publish(event);

        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(senderId.toString()),
                eq(RealtimeMessagePublisher.USER_MESSAGE_QUEUE),
                payloadCaptor.capture()
        );
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(recipientId.toString()),
                eq(RealtimeMessagePublisher.USER_MESSAGE_QUEUE),
                payloadCaptor.capture()
        );

        List<RealtimeMessageEvent> payloads = payloadCaptor.getAllValues().stream()
                .map(RealtimeMessageEvent.class::cast)
                .toList();
        assertThat(payloads).extracting(payload -> payload.message().mine())
                .containsExactlyInAnyOrder(true, false);
        assertThat(payloads).allSatisfy(payload -> {
            assertThat(payload.type()).isEqualTo("MESSAGE_CREATED");
            assertThat(payload.chatType()).isEqualTo(ChatType.DIRECT);
        });
    }

    @Test
    void savedMessageIsDeliveredOnlyToItsOwner() {
        UUID ownerId = UUID.randomUUID();
        MessageCreatedEvent event = event(ChatType.SAVED, ownerId, List.of(ownerId));

        publisher.publish(event);

        verify(messagingTemplate).convertAndSendToUser(
                eq(ownerId.toString()),
                eq(RealtimeMessagePublisher.USER_MESSAGE_QUEUE),
                org.mockito.ArgumentMatchers.any(RealtimeMessageEvent.class)
        );
    }

    private MessageCreatedEvent event(ChatType type, UUID senderId, List<UUID> recipients) {
        return new MessageCreatedEvent(
                UUID.randomUUID(),
                type,
                UUID.randomUUID(),
                1,
                senderId,
                "Hello",
                LocalDateTime.now(),
                recipients
        );
    }
}
