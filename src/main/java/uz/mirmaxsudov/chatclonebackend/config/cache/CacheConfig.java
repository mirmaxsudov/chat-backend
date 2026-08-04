package uz.mirmaxsudov.chatclonebackend.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {
    public static final String USER_ROLES = "userRoles";
    public static final String USER_PERMISSIONS = "userPermissions";
    public static final String USER_AUTHORITIES = "userAuthorities";
    public static final String AUTH_ME = "authMe";
    public static final String TOTAL_USERS_COUNT = "totalUsersCount";
    public static final String TOTAL_ACTIVE_COURSES = "totalActiveCourses";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(List.of(
                new CaffeineCache(TOTAL_ACTIVE_COURSES, Caffeine.newBuilder()
                        .maximumSize(10_100)
                        .expireAfterWrite(Duration.ofHours(1))
                        .recordStats()
                        .build()),
                new CaffeineCache(TOTAL_USERS_COUNT, Caffeine.newBuilder()
                        .maximumSize(10_100)
                        .expireAfterWrite(Duration.ofHours(1))
                        .recordStats()
                        .build()),
                new CaffeineCache(USER_ROLES, Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build()),
                new CaffeineCache(USER_PERMISSIONS, Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build()),
                new CaffeineCache(USER_AUTHORITIES, Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(3))
                        .recordStats()
                        .build()),
                new CaffeineCache(AUTH_ME, Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(3))
                        .recordStats()
                        .build())
        ));

        return cacheManager;
    }
}
