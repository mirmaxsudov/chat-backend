package uz.mirmaxsudov.chatclonebackend.model.response.presence;

import uz.mirmaxsudov.chatclonebackend.model.enums.presence.PresenceStatus;

import java.time.Instant;
import java.util.UUID;

public record UserPresenceResponse(
        UUID userId,
        PresenceStatus status,
        Instant lastSeenAt,
        Instant changedAt
) {
}