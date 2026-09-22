package uz.mirmaxsudov.chatclonebackend.config.minio;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "attachments.media-preview")
public class MediaPreviewProperties {
    private boolean enabled = true;
    private int maxWidth = 640;
    private int maxHeight = 960;
    private float jpegQuality = 0.70f;
    private long maxSourcePixels = 250_000_000L;
    private int maxAttempts = 3;
    private int recoveryBatchSize = 100;
    private Duration processingTimeout = Duration.ofMinutes(10);
    private int corePoolSize = 1;
    private int maxPoolSize = 2;
    private int queueCapacity = 50;
}
