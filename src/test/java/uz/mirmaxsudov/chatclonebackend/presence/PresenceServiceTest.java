package uz.mirmaxsudov.chatclonebackend.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uz.mirmaxsudov.chatclonebackend.listener.chat.RealtimePresencePublisher;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.presence.PresenceStatus;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.UserPresenceResponse;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.presence.PresenceRegistry;
import uz.mirmaxsudov.chatclonebackend.service.presence.PresenceService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T10:15:30Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RealtimePresencePublisher publisher;

    private PresenceService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        PresenceRegistry registry = new PresenceRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new PresenceService(registry, userRepository, publisher);
        userId = UUID.randomUUID();
    }

    @Test
    void publishesOnlyUserLevelTransitionsAcrossMultipleSessions() {
        when(userRepository.updateLastSeenAt(userId, NOW)).thenReturn(1);

        service.connected(userId, "session-one");
        service.connected(userId, "session-two");
        service.disconnected("session-one");
        service.disconnected("session-two");

        verify(publisher, times(1)).publish(new UserPresenceResponse(
                userId,
                PresenceStatus.ONLINE,
                null,
                NOW
        ));
        verify(publisher, times(1)).publish(new UserPresenceResponse(
                userId,
                PresenceStatus.OFFLINE,
                NOW,
                NOW
        ));
        verify(userRepository).updateLastSeenAt(userId, NOW);
    }

    @Test
    void unknownDisconnectDoesNothing() {
        service.disconnected("missing-session");

        verify(userRepository, never()).updateLastSeenAt(eq(userId), eq(NOW));
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistenceFailureDoesNotLeavePeersWithStaleOnlineState() {
        when(userRepository.updateLastSeenAt(userId, NOW))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        service.connected(userId, "session-one");
        clearInvocations(publisher);

        service.disconnected("session-one");

        verify(publisher).publish(new UserPresenceResponse(
                userId,
                PresenceStatus.OFFLINE,
                NOW,
                NOW
        ));
    }

    @Test
    void snapshotCombinesLiveStateWithPersistedLastSeen() {
        Instant previousLastSeen = NOW.minusSeconds(60);
        User user = User.builder().lastSeenAt(previousLastSeen).build();
        user.setId(userId);

        assertThat(service.getPresence(user)).isEqualTo(new UserPresenceResponse(
                userId,
                PresenceStatus.OFFLINE,
                previousLastSeen,
                previousLastSeen
        ));

        service.connected(userId, "session-one");

        assertThat(service.getPresence(user)).isEqualTo(new UserPresenceResponse(
                userId,
                PresenceStatus.ONLINE,
                previousLastSeen,
                NOW
        ));
    }
}
