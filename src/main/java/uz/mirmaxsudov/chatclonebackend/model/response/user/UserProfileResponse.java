package uz.mirmaxsudov.chatclonebackend.model.response.user;

import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String phoneNumber,
        String username,
        String firstname,
        String lastname,
        Set<RoleName> roles
) {
}
