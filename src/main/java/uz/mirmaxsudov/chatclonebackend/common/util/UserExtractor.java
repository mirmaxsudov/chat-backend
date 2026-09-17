package uz.mirmaxsudov.chatclonebackend.common.util;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface UserExtractor {
    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
