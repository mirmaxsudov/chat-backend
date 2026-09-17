package uz.mirmaxsudov.chatclonebackend.service.tus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;
import uz.mirmaxsudov.chatclonebackend.storage.StoredObject;
import uz.mirmaxsudov.chatclonebackend.tus.TusUploadStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleChunkCleanupServiceTest {
    private StorageService storageService;
    private TusUploadStore uploadStore;
    private StaleChunkCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        uploadStore = new TusUploadStore();

        TusProperties properties = new TusProperties();
        properties.setStaleChunkRetention(Duration.ofDays(1));
        cleanupService = new StaleChunkCleanupService(storageService, uploadStore, properties);
    }

    @Test
    void removesOrphanedChunkGroupsInactiveForMoreThanOneDay() {
        Instant now = Instant.parse("2026-09-16T12:00:00Z");
        List<StoredObject> staleChunks = List.of(
                new StoredObject("uploads/.tus/orphan/0", now.minus(Duration.ofDays(2))),
                new StoredObject("uploads/.tus/orphan/10", now.minus(Duration.ofHours(25)))
        );
        when(storageService.listObjects(StaleChunkCleanupService.CHUNK_PREFIX)).thenReturn(staleChunks);

        cleanupService.cleanupStaleChunks(now);

        verify(storageService).removeObjects(List.of(
                "uploads/.tus/orphan/0",
                "uploads/.tus/orphan/10"
        ));
    }

    @Test
    void keepsWholeUploadWhenItsNewestChunkIsRecent() {
        Instant now = Instant.parse("2026-09-16T12:00:00Z");
        when(storageService.listObjects(StaleChunkCleanupService.CHUNK_PREFIX)).thenReturn(List.of(
                new StoredObject("uploads/.tus/active/0", now.minus(Duration.ofDays(2))),
                new StoredObject("uploads/.tus/active/10", now.minus(Duration.ofHours(1)))
        ));

        cleanupService.cleanupStaleChunks(now);

        verify(storageService, never()).removeObjects(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void removesExpiredIncompleteUploadFromRegistry() {
        TusUpload upload = uploadStore.create("stale", "uploads/stale", 100, Map.of(), UUID.randomUUID());
        Instant future = Instant.now().plus(Duration.ofDays(2));
        List<String> chunkKeys = List.of("uploads/.tus/stale/0");
        when(storageService.listObjects(StaleChunkCleanupService.CHUNK_PREFIX)).thenReturn(List.of(
                new StoredObject(chunkKeys.getFirst(), future.minus(Duration.ofHours(25)))
        ));

        cleanupService.cleanupStaleChunks(future);

        verify(storageService).removeObjects(chunkKeys);
        assertTrue(uploadStore.findById(upload.getId()).isEmpty());
    }

    @Test
    void retainsCompletedUploadRecordAfterRemovingItsStagingChunks() {
        TusUpload upload = uploadStore.create("complete", "uploads/complete", 10, Map.of(), UUID.randomUUID());
        upload.markCompleted(UUID.randomUUID());
        Instant future = Instant.now().plus(Duration.ofDays(2));
        List<String> chunkKeys = List.of("uploads/.tus/complete/0");
        when(storageService.listObjects(StaleChunkCleanupService.CHUNK_PREFIX)).thenReturn(List.of(
                new StoredObject(chunkKeys.getFirst(), future.minus(Duration.ofHours(25)))
        ));

        cleanupService.cleanupStaleChunks(future);

        verify(storageService).removeObjects(chunkKeys);
        assertTrue(uploadStore.findById(upload.getId()).isPresent());
    }
}
