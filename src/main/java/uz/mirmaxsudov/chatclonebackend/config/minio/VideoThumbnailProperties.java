package uz.mirmaxsudov.chatclonebackend.config.minio;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "attachments.video-thumbnail")
public class VideoThumbnailProperties {
    private boolean enabled = true;
    private int maxWidth = 640;
    private int corePoolSize = 1;
    private int maxPoolSize = 2;
    private int queueCapacity = 20;
}
