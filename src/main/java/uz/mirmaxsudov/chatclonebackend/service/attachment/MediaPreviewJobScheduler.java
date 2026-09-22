package uz.mirmaxsudov.chatclonebackend.service.attachment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.MediaPreviewProperties;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static uz.mirmaxsudov.chatclonebackend.config.minio.MediaPreviewJobConfig.MEDIA_PREVIEW_EXECUTOR;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaPreviewJobScheduler {
    private static final String LEGACY_DIMENSION_LIMIT_ERROR =
            "Media dimensions exceed the configured preview limit";

    private final Executor executor;
    private final MediaPreviewService mediaPreviewService;
    private final AttachmentRepository attachmentRepository;
    private final MediaPreviewProperties properties;
    private final Clock clock;

    public MediaPreviewJobScheduler(
            @Qualifier(MEDIA_PREVIEW_EXECUTOR) Executor executor,
            MediaPreviewService mediaPreviewService,
            AttachmentRepository attachmentRepository,
            MediaPreviewProperties properties,
            Clock clock
    ) {
        this.executor = executor;
        this.mediaPreviewService = mediaPreviewService;
        this.attachmentRepository = attachmentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean submit(UUID attachmentId) {
        if (!mediaPreviewService.isEnabled())
            return false;

        try {
            executor.execute(() -> mediaPreviewService.generatePreview(attachmentId));
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("Media preview queue is full; attachment remains pending: attachmentId={}", attachmentId);
            return false;
        }
    }

    @Scheduled(
            fixedDelayString = "${attachments.media-preview.recovery-interval-ms:60000}",
            initialDelayString = "${attachments.media-preview.recovery-initial-delay-ms:30000}"
    )
    public void recoverPendingPreviews() {
        if (!mediaPreviewService.isEnabled())
            return;

        List<AttachmentType> previewableTypes = List.of(AttachmentType.IMAGE, AttachmentType.VIDEO);
        int initialized = attachmentRepository.initializeMissingPreviewableStatuses(
                previewableTypes,
                PreviewStatus.PENDING,
                clock.instant()
        );
        attachmentRepository.initializeMissingNonPreviewableStatuses(
                previewableTypes,
                PreviewStatus.NOT_APPLICABLE,
                clock.instant()
        );
        if (initialized > 0)
            log.info("Queued {} existing attachments for media preview generation", initialized);

        int legacyFailuresReset = attachmentRepository.resetFailedPreviewsByError(
                PreviewStatus.FAILED,
                PreviewStatus.PENDING,
                LEGACY_DIMENSION_LIMIT_ERROR,
                clock.instant()
        );
        if (legacyFailuresReset > 0)
            log.info("Requeued {} previews rejected by the former source-dimension limit", legacyFailuresReset);

        var staleBefore = clock.instant().minus(properties.getProcessingTimeout());
        attachmentRepository.failExhaustedStalePreviews(
                PreviewStatus.PROCESSING,
                PreviewStatus.FAILED,
                staleBefore,
                properties.getMaxAttempts(),
                clock.instant()
        );
        int reset = attachmentRepository.resetStalePreviews(
                PreviewStatus.PROCESSING,
                PreviewStatus.PENDING,
                staleBefore,
                properties.getMaxAttempts(),
                clock.instant()
        );
        if (reset > 0)
            log.info("Reset {} stale media preview jobs", reset);

        var pending = attachmentRepository
                .findTop100ByPreviewStatusAndDeletedFalseOrderByCreatedAtAsc(PreviewStatus.PENDING);
        int limit = Math.min(pending.size(), properties.getRecoveryBatchSize());
        for (int index = 0; index < limit; index++) {
            if (!submit(pending.get(index).getId()))
                break;
        }
    }
}
