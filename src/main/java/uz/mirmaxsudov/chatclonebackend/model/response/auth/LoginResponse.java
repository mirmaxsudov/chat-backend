package uz.mirmaxsudov.chatclonebackend.model.response.auth;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {
}
