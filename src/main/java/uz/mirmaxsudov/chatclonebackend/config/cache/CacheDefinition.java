package uz.mirmaxsudov.chatclonebackend.config.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum CacheDefinition {
    AUTH_ME(CacheNames.AUTH_ME, 10_000, Duration.ofHours(3)),
    USER_PROFILE_RESPONSE(CacheNames.USER_PROFILE_RESPONSE, 10_000, Duration.ofMinutes(30));

    public final String cacheName;
    public final long maximumSize;
    public final Duration ttl;
}
