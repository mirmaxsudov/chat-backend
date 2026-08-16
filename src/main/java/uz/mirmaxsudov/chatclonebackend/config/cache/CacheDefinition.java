package uz.mirmaxsudov.chatclonebackend.config.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum CacheDefinition {
    TOTAL_USERS_COUNT("totalUsersCount", 10_100, Duration.ofHours(1)),
    AUTH_ME("authMe", 10_000, Duration.ofHours(3));
    private final String cacheName;
    private final long maximumSize;
    private final Duration ttl;
}