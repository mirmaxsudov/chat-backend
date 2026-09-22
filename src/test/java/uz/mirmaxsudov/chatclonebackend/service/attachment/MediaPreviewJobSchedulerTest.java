package uz.mirmaxsudov.chatclonebackend.service.attachment;

import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.config.minio.MediaPreviewProperties;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaPreviewJobSchedulerTest {
    private final MediaPreviewService mediaPreviewService = mock(MediaPreviewService.class);
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final MediaPreviewProperties properties = new MediaPreviewProperties();

    @Test
    void submitsPreviewWorkWithoutRunningItOnCallingThread() {
        UUID attachmentId = UUID.randomUUID();
        CapturingExecutor executor = new CapturingExecutor();
        MediaPreviewJobScheduler scheduler = scheduler(executor);
        when(mediaPreviewService.isEnabled()).thenReturn(true);

        assertThat(scheduler.submit(attachmentId)).isTrue();
        verify(mediaPreviewService, org.mockito.Mockito.never()).generatePreview(attachmentId);

        executor.task.run();

        verify(mediaPreviewService).generatePreview(attachmentId);
    }

    @Test
    void queueRejectionLeavesAttachmentPendingForRecovery() {
        Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };
        MediaPreviewJobScheduler scheduler = scheduler(rejectingExecutor);
        when(mediaPreviewService.isEnabled()).thenReturn(true);

        assertThat(scheduler.submit(UUID.randomUUID())).isFalse();
    }

    private MediaPreviewJobScheduler scheduler(Executor executor) {
        return new MediaPreviewJobScheduler(
                executor,
                mediaPreviewService,
                attachmentRepository,
                properties,
                Clock.systemUTC()
        );
    }

    private static final class CapturingExecutor implements Executor {
        private Runnable task;

        @Override
        public void execute(Runnable command) {
            task = command;
        }
    }
}
