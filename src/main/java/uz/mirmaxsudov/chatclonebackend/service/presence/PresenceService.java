package uz.mirmaxsudov.chatclonebackend.service.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.listener.chat.RealtimePresencePublisher;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.presence.PresenceStatus;
import uz.mirmaxsudov.chatclonebackend.model.response.presence.UserPresenceResponse;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {
    private final PresenceRegistry registry;
    private final UserRepository userRepository;
    private final RealtimePresencePublisher publisher;

    public void connected(UUID userId, String sessionId) {
        registry.connect(userId, sessionId).ifPresent(onlineSince -> {
            UserPresenceResponse presence = new UserPresenceResponse(
                    userId,
                    PresenceStatus.ONLINE,
                    null,
                    onlineSince
            );
            publisher.publish(presence);
            log.debug("User became online: userId={}, session={}", userId, sessionId);
        });
    }

    public void disconnected(String sessionId) {
        registry.disconnect(sessionId).ifPresent(disconnectedUser -> {
            UUID userId = disconnectedUser.userId();
            Instant disconnectedAt = disconnectedUser.disconnectedAt();
            try {
                int updatedUsers = userRepository.updateLastSeenAt(userId, disconnectedAt);
                if (updatedUsers == 0)
                    log.warn("Could not persist last seen for missing user: userId={}", userId);
            } catch (DataAccessException exception) {
                log.error("Failed to persist last seen: userId={}", userId, exception);
            }

            UserPresenceResponse presence = new UserPresenceResponse(
                    userId,
                    PresenceStatus.OFFLINE,
                    disconnectedAt,
                    disconnectedAt
            );
            publisher.publish(presence);
            log.debug("User became offline: userId={}, session={}", userId, sessionId);
        });
    }

    public UserPresenceResponse getPresence(User user) {
        return registry.onlineSince(user.getId())
                .map(onlineSince -> new UserPresenceResponse(
                        user.getId(),
                        PresenceStatus.ONLINE,
                        user.getLastSeenAt(),
                        onlineSince
                ))
                .orElseGet(() -> new UserPresenceResponse(
                        user.getId(),
                        PresenceStatus.OFFLINE,
                        user.getLastSeenAt(),
                        user.getLastSeenAt()
                ));
    }
}
