package uz.mirmaxsudov.chatclonebackend.model.response.user;

import java.util.UUID;

public record PublicUserResponse(
        UUID id,
        String username,
        String firstname,
        String lastname
) {
}
