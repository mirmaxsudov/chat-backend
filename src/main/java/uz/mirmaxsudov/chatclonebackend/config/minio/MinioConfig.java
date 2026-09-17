package uz.mirmaxsudov.chatclonebackend.config.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
        MinioProperties.class,
        TusProperties.class,
        VideoThumbnailProperties.class
})
public class MinioConfig {
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());

        if (properties.getRegion() != null && !properties.getRegion().isBlank())
            builder.region(properties.getRegion());

        return builder.build();
    }
}
