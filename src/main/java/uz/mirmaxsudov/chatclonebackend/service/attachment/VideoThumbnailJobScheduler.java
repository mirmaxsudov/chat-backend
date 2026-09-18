package uz.mirmaxsudov.chatclonebackend.service.attachment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static uz.mirmaxsudov.chatclonebackend.config.minio.VideoThumbnailJobConfig.VIDEO_THUMBNAIL_EXECUTOR;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VideoThumbnailJobScheduler {
    private final Executor executor;
    private final VideoThumbnailService videoThumbnailService;

    public VideoThumbnailJobScheduler(
            @Qualifier(VIDEO_THUMBNAIL_EXECUTOR) Executor executor,
            VideoThumbnailService videoThumbnailService
    ) {
        this.executor = executor;
        this.videoThumbnailService = videoThumbnailService;
    }

    public boolean submit(String sourceStorageKey, String sourceIdentifier, String thumbnailStorageKey) {
        if (!videoThumbnailService.isEnabled())
            return false;

        try {
            executor.execute(() -> videoThumbnailService.generateThumbnail(
                    sourceStorageKey,
                    sourceIdentifier,
                    thumbnailStorageKey
            ));
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("Video thumbnail queue is full; skipping attachment {}", sourceIdentifier);
            return false;
        }
    }
}
