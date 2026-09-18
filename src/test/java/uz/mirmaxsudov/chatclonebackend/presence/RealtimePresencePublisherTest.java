package uz.mirmaxsudov.chatclonebackend.presence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uz.mirmaxsudov.chatclonebackend.listener.chat.RealtimePresencePublisher;
import uz.mirmaxsudov.chatclonebackend.model.enums.presence.PresenceStatus;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.RealtimePresenceEvent;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.UserPresenceResponse;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimePresencePublisherTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @InjectMocks
    private RealtimePresencePublisher publisher;

    @Test
    void publishesOnlyToDirectChatPeers() {
        UUID userId = UUID.randomUUID();
        UUID firstPeerId = UUID.randomUUID();
        UUID secondPeerId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-09-18T10:15:30Z");
        when(chatMemberRepository.findActiveDirectPeerIds(userId))
                .thenReturn(List.of(firstPeerId, secondPeerId));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        publisher.publish(new UserPresenceResponse(
                userId,
                PresenceStatus.OFFLINE,
                changedAt,
                changedAt
        ));

        verify(messagingTemplate).convertAndSendToUser(
                eq(firstPeerId.toString()),
                eq(RealtimePresencePublisher.USER_PRESENCE_QUEUE),
                payload.capture()
        );
        verify(messagingTemplate).convertAndSendToUser(
                eq(secondPeerId.toString()),
                eq(RealtimePresencePublisher.USER_PRESENCE_QUEUE),
                payload.capture()
        );
        assertThat(payload.getAllValues()).allSatisfy(value -> {
            RealtimePresenceEvent event = (RealtimePresenceEvent) value;
            assertThat(event.type()).isEqualTo("PRESENCE_CHANGED");
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.status()).isEqualTo(PresenceStatus.OFFLINE);
            assertThat(event.changedAt()).isEqualTo(changedAt);
        });
    }
}
