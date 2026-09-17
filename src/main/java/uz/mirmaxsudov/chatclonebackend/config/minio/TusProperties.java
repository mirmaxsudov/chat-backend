package uz.mirmaxsudov.chatclonebackend.config.minio;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "tus")
public class TusProperties {
    private long maxUploadSizeBytes = 10L * 1024 * 1024 * 1024; // 10GB
    private boolean chunkCleanupOnComplete = true;
    private Duration staleChunkRetention = Duration.ofDays(1);
}
