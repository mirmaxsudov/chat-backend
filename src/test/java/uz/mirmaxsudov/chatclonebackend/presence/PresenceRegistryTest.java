package uz.mirmaxsudov.chatclonebackend.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.service.presence.PresenceRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-18T10:15:30Z");

    private PresenceRegistry registry;
    private UUID userId;

    @BeforeEach
    void setUp() {
        registry = new PresenceRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    @Test
    void firstSessionTransitionsOnlineAndFinalSessionTransitionsOffline() {
        assertThat(registry.connect(userId, "session-one")).contains(NOW);
        assertThat(registry.connect(userId, "session-two")).isEmpty();
        assertThat(registry.onlineSince(userId)).contains(NOW);

        assertThat(registry.disconnect("session-one")).isEmpty();
        assertThat(registry.onlineSince(userId)).contains(NOW);

        assertThat(registry.disconnect("session-two"))
                .contains(new PresenceRegistry.DisconnectedUser(userId, NOW));
        assertThat(registry.onlineSince(userId)).isEmpty();
    }

    @Test
    void duplicateSessionEventsAreIdempotent() {
        assertThat(registry.connect(userId, "session-one")).contains(NOW);
        assertThat(registry.connect(userId, "session-one")).isEmpty();

        assertThat(registry.disconnect("session-one")).isPresent();
        assertThat(registry.disconnect("session-one")).isEmpty();
    }
}
