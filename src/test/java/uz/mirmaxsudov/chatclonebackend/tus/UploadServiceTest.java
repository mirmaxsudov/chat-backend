package uz.mirmaxsudov.chatclonebackend.tus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.service.tus.UploadService;
import uz.mirmaxsudov.chatclonebackend.storage.StorageException;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class UploadServiceTest {
    private StorageService storageService;
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);

        TusProperties properties = new TusProperties();
        properties.setMaxUploadSizeBytes(100);
        properties.setChunkCleanupOnComplete(true);

        uploadService = new UploadService(new TusUploadStore(), storageService, properties);
    }

    @Test
    void appendsChunksAndCompletesUploadInOffsetOrder() {
        TusUpload upload = uploadService.createUpload(5, Map.of("filename", "hello.txt"));

        assertEquals(3, uploadService.appendChunk(
                upload.getId(),
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));
        assertEquals(5, uploadService.appendChunk(
                upload.getId(),
                3,
                2,
                new ByteArrayInputStream(new byte[]{4, 5})
        ));

        List<String> chunks = List.of(
                "uploads/.tus/" + upload.getId() + "/0",
                "uploads/.tus/" + upload.getId() + "/3"
        );

        assertTrue(upload.isCompleted());
        verify(storageService).composeObject(upload.getObjectKey(), chunks);
        verify(storageService).removeObjects(chunks);
    }

    @Test
    void rejectsMismatchedOffsetBeforeWritingAChunk() {
        TusUpload upload = uploadService.createUpload(5, Map.of());

        assertThrows(TusProtocolException.class, () -> uploadService.appendChunk(
                upload.getId(),
                2,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));

        assertEquals(0, upload.getOffset());
        verify(storageService, never()).uploadObject(any(), any(), eq(3L), any(), any());
    }

    @Test
    void rejectsUploadsAboveConfiguredMaximum() {
        assertThrows(TusProtocolException.class, () -> uploadService.createUpload(101, Map.of()));
    }

    @Test
    void leavesOffsetRetryableWhenFinalCompositionFails() {
        TusUpload upload = uploadService.createUpload(3, Map.of());
        doThrow(new StorageException("compose failed"))
                .when(storageService)
                .composeObject(eq(upload.getObjectKey()), any());

        assertThrows(StorageException.class, () -> uploadService.appendChunk(
                upload.getId(),
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));

        assertEquals(0, upload.getOffset());
        assertTrue(upload.getChunks().isEmpty());

        reset(storageService);
        assertEquals(3, uploadService.appendChunk(
                upload.getId(),
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));
        assertTrue(upload.isCompleted());
    }
}
