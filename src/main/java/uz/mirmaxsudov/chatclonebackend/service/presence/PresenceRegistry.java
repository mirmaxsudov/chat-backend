package uz.mirmaxsudov.chatclonebackend.service.presence;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class PresenceRegistry {
    private final Map<UUID, UserSessions> sessionsByUser = new HashMap<>();
    private final Map<String, UUID> usersBySession = new HashMap<>();
    private final Clock clock;

    public PresenceRegistry(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<Instant> connect(UUID userId, String sessionId) {
        UUID existingUserId = usersBySession.putIfAbsent(sessionId, userId);

        if (existingUserId != null)
            return Optional.empty();

        UserSessions current = sessionsByUser.get(userId);
        if (current != null) {
            current.sessionIds().add(sessionId);
            return Optional.empty();
        }

        Instant onlineSince = clock.instant();
        sessionsByUser.put(userId, new UserSessions(new HashSet<>(Set.of(sessionId)), onlineSince));
        return Optional.of(onlineSince);
    }

    public synchronized Optional<DisconnectedUser> disconnect(String sessionId) {
        UUID userId = usersBySession.remove(sessionId);
        if (userId == null)
            return Optional.empty();

        UserSessions current = sessionsByUser.get(userId);
        if (current == null) {
            return Optional.empty();
        }

        current.sessionIds().remove(sessionId);

        if (!current.sessionIds().isEmpty())
            return Optional.empty();

        sessionsByUser.remove(userId);
        return Optional.of(new DisconnectedUser(userId, clock.instant()));
    }

    public synchronized Optional<Instant> onlineSince(UUID userId) {
        UserSessions sessions = sessionsByUser.get(userId);
        return sessions == null ? Optional.empty() : Optional.of(sessions.onlineSince());
    }

    record UserSessions(Set<String> sessionIds, Instant onlineSince) {
    }

    public record DisconnectedUser(UUID userId, Instant disconnectedAt) {
    }
}
