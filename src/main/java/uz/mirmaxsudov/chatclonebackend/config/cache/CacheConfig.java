package uz.mirmaxsudov.chatclonebackend.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(
                Arrays.stream(CacheDefinition.values())
                        .map(this::createCache)
                        .toList()
        );

        return cacheManager;
    }

    public CaffeineCache createCache(CacheDefinition definition) {
        return new CaffeineCache(
                definition.getCacheName(),
                Caffeine.newBuilder()
                        .maximumSize(definition.getMaximumSize())
                        .expireAfterWrite(definition.getTtl())
                        .recordStats()
                        .build()
        );
    }
}
