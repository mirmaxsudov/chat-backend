package uz.mirmaxsudov.chatclonebackend.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secretKey,
        long accessExpirationMs,
        long refreshExpirationMs,
        String issuer,
        String audience
) {
    public JwtProperties {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT secret key must contain at least 32 bytes");

        if (accessExpirationMs <= 0)
            throw new IllegalArgumentException("JWT access expiration must be positive");

        if (refreshExpirationMs <= 0)
            throw new IllegalArgumentException("JWT refresh expiration must be positive");

        if (issuer == null || issuer.isBlank())
            throw new IllegalArgumentException("JWT issuer is required");

        if (audience == null || audience.isBlank())
            throw new IllegalArgumentException("JWT audience is required");
    }
}
