package uz.mirmaxsudov.chatclonebackend.service.attachment;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoThumbnailJobSchedulerTest {
    private final VideoThumbnailService videoThumbnailService = mock(VideoThumbnailService.class);

    @Test
    void submitsThumbnailWorkWithoutRunningItOnTheCallingThread() {
        CapturingExecutor executor = new CapturingExecutor();
        VideoThumbnailJobScheduler scheduler = new VideoThumbnailJobScheduler(executor, videoThumbnailService);
        when(videoThumbnailService.isEnabled()).thenReturn(true);

        assertTrue(scheduler.submit("uploads/video", "upload-id", "thumbnails/upload-id.jpg"));
        verify(videoThumbnailService, org.mockito.Mockito.never())
                .generateThumbnail("uploads/video", "upload-id", "thumbnails/upload-id.jpg");

        executor.task.run();

        verify(videoThumbnailService)
                .generateThumbnail("uploads/video", "upload-id", "thumbnails/upload-id.jpg");
    }

    @Test
    void queueRejectionDoesNotEscapeIntoTheUploadFlow() {
        Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };
        VideoThumbnailJobScheduler scheduler = new VideoThumbnailJobScheduler(
                rejectingExecutor,
                videoThumbnailService
        );
        when(videoThumbnailService.isEnabled()).thenReturn(true);

        assertFalse(scheduler.submit("uploads/video", "upload-id", "thumbnails/upload-id.jpg"));
    }

    private static final class CapturingExecutor implements Executor {
        private Runnable task;

        @Override
        public void execute(Runnable command) {
            task = command;
        }
    }
}
