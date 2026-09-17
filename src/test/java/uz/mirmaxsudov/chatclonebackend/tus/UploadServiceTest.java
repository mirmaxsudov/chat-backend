package uz.mirmaxsudov.chatclonebackend.tus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentService;
import uz.mirmaxsudov.chatclonebackend.service.tus.UploadService;
import uz.mirmaxsudov.chatclonebackend.storage.StorageException;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceTest {
    private StorageService storageService;
    private AttachmentService attachmentService;
    private UploadService uploadService;
    private UUID uploaderId;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        attachmentService = mock(AttachmentService.class);
        uploaderId = UUID.randomUUID();

        TusProperties properties = new TusProperties();
        properties.setMaxUploadSizeBytes(100);
        properties.setChunkCleanupOnComplete(true);

        uploadService = new UploadService(new TusUploadStore(), storageService, properties, attachmentService);
        when(attachmentService.createCompletedAttachment(any(), anyLong(), any(), eq(uploaderId)))
                .thenAnswer(invocation -> {
                    Attachment attachment = new Attachment();
                    attachment.setId(UUID.randomUUID());
                    return attachment;
                });
    }

    @Test
    void appendsChunksAndCompletesUploadInOffsetOrder() {
        TusUpload upload = uploadService.createUpload(5, Map.of("filename", "hello.txt"), uploaderId);

        assertEquals(3, uploadService.appendChunk(
                upload.getId(),
                uploaderId,
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));
        assertEquals(5, uploadService.appendChunk(
                upload.getId(),
                uploaderId,
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
        verify(attachmentService).createCompletedAttachment(
                upload.getObjectKey(),
                5,
                Map.of("filename", "hello.txt"),
                uploaderId
        );
    }

    @Test
    void rejectsMismatchedOffsetBeforeWritingAChunk() {
        TusUpload upload = uploadService.createUpload(5, Map.of(), uploaderId);

        assertThrows(TusProtocolException.class, () -> uploadService.appendChunk(
                upload.getId(),
                uploaderId,
                2,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));

        assertEquals(0, upload.getOffset());
        verify(storageService, never()).uploadObject(any(), any(), eq(3L), any(), any());
    }

    @Test
    void hidesAnUploadFromAnotherUser() {
        TusUpload upload = uploadService.createUpload(5, Map.of(), uploaderId);

        TusProtocolException exception = assertThrows(TusProtocolException.class, () -> uploadService.getUpload(
                upload.getId(),
                UUID.randomUUID()
        ));

        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void rejectsUploadsAboveConfiguredMaximum() {
        assertThrows(TusProtocolException.class, () -> uploadService.createUpload(101, Map.of(), uploaderId));
    }

    @Test
    void leavesOffsetRetryableWhenFinalCompositionFails() {
        TusUpload upload = uploadService.createUpload(3, Map.of(), uploaderId);
        doThrow(new StorageException("compose failed"))
                .when(storageService)
                .composeObject(eq(upload.getObjectKey()), any());

        assertThrows(StorageException.class, () -> uploadService.appendChunk(
                upload.getId(),
                uploaderId,
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));

        assertEquals(0, upload.getOffset());
        assertTrue(upload.getChunks().isEmpty());

        reset(storageService);
        assertEquals(3, uploadService.appendChunk(
                upload.getId(),
                uploaderId,
                0,
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        ));
        assertTrue(upload.isCompleted());
    }
}
