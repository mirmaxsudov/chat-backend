package uz.mirmaxsudov.chatclonebackend.service.attachment;

import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.mirmaxsudov.chatclonebackend.exceptions.AttachmentRangeNotSatisfiableException;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.response.attachment.AttachmentClientResponse;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentServiceTest {
    private static final UUID ATTACHMENT_ID = UUID.randomUUID();
    private static final String STORAGE_KEY = "uploads/video";

    private StorageService storageService;
    private AttachmentRepository attachmentRepository;
    private UserRepository userRepository;
    private AttachmentService attachmentService;
    private StatObjectResponse stat;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        attachmentRepository = mock(AttachmentRepository.class);
        userRepository = mock(UserRepository.class);
        attachmentService = new AttachmentService(storageService, attachmentRepository, userRepository);

        Attachment attachment = Attachment.builder()
                .storageKey(STORAGE_KEY)
                .originalFileName("video.mp4")
                .contentType("video/mp4")
                .type(AttachmentType.VIDEO)
                .sizeBytes(1_000)
                .build();
        stat = mock(StatObjectResponse.class);

        when(attachmentRepository.findByIdAndDeletedFalse(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));
        when(storageService.statObject(STORAGE_KEY)).thenReturn(stat);
        when(stat.size()).thenReturn(1_000L);
        when(stat.contentType()).thenReturn("video/mp4");
    }

    @Test
    void opensTheWholeObjectWhenRangeIsMissing() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(storageService.openObject(STORAGE_KEY, 0, null)).thenReturn(stream);

        AttachmentClientResponse response = attachmentService.getClientAttachment(
                ATTACHMENT_ID,
                null,
                true
        );

        assertFalse(response.partial());
        assertEquals(1_000, response.totalSize());
        assertEquals(1_000, response.contentLength());
        assertEquals(0, response.start());
        assertEquals(999, response.end());
        assertSame(stream, response.stream());
    }

    @Test
    void opensOnlyTheRequestedByteRange() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(storageService.openObject(STORAGE_KEY, 100, 100L)).thenReturn(stream);

        AttachmentClientResponse response = attachmentService.getClientAttachment(
                ATTACHMENT_ID,
                "bytes=100-199",
                true
        );

        assertTrue(response.partial());
        assertEquals(100, response.start());
        assertEquals(199, response.end());
        assertEquals(100, response.contentLength());
        assertSame(stream, response.stream());
        verify(storageService).openObject(STORAGE_KEY, 100, 100L);
    }

    @Test
    void supportsSuffixRanges() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(storageService.openObject(STORAGE_KEY, 900, 100L)).thenReturn(stream);

        AttachmentClientResponse response = attachmentService.getClientAttachment(
                ATTACHMENT_ID,
                "bytes=-100",
                true
        );

        assertEquals(900, response.start());
        assertEquals(999, response.end());
        assertEquals(100, response.contentLength());
    }

    @Test
    void headRequestDoesNotOpenTheObjectStream() {
        AttachmentClientResponse response = attachmentService.getClientAttachment(
                ATTACHMENT_ID,
                "bytes=0-99",
                false
        );

        assertNull(response.stream());
        assertEquals(100, response.contentLength());
        verify(storageService, never()).openObject(STORAGE_KEY, 0, 100L);
    }

    @Test
    void opensGeneratedVideoThumbnail() {
        String thumbnailStorageKey = "thumbnails/video.jpg";
        Attachment attachment = Attachment.builder()
                .storageKey(STORAGE_KEY)
                .originalFileName("video.mp4")
                .contentType("video/mp4")
                .type(AttachmentType.VIDEO)
                .sizeBytes(1_000)
                .metadata(Map.of(
                        AttachmentService.THUMBNAIL_STORAGE_KEY_METADATA,
                        thumbnailStorageKey
                ))
                .build();
        StatObjectResponse thumbnailStat = mock(StatObjectResponse.class);
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(attachmentRepository.findByIdAndDeletedFalse(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));
        when(storageService.statObject(thumbnailStorageKey)).thenReturn(thumbnailStat);
        when(thumbnailStat.size()).thenReturn(100L);
        when(storageService.openObject(thumbnailStorageKey, 0, null)).thenReturn(stream);

        AttachmentClientResponse response = attachmentService.getVideoThumbnail(
                ATTACHMENT_ID,
                null,
                true
        );

        assertEquals("image/jpeg", response.contentType());
        assertEquals("video.mp4.jpg", response.fileName());
        assertEquals(100, response.contentLength());
        assertSame(stream, response.stream());
    }

    @Test
    void rejectsUnsatisfiableRanges() {
        AttachmentRangeNotSatisfiableException exception = assertThrows(
                AttachmentRangeNotSatisfiableException.class,
                () -> attachmentService.getClientAttachment(
                        ATTACHMENT_ID,
                        "bytes=1000-",
                        true
                )
        );

        assertEquals(1_000, exception.getTotalSize());
        verify(storageService, never()).openObject(STORAGE_KEY, 1_000, null);
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, IMAGE",
            "video/mp4, VIDEO",
            "audio/mpeg, AUDIO",
            "application/pdf, PDF",
            "application/vnd.ms-excel, EXCEL",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, EXCEL",
            "text/csv, EXCEL",
            "application/vnd.ms-powerpoint, PPT",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation, PPT",
            "application/zip, OTHERS",
            "' IMAGE/PNG; charset=UTF-8 ', IMAGE"
    })
    void resolvesAttachmentTypeFromContentType(String contentType, AttachmentType expected) {
        assertEquals(expected, attachmentService.resolveAttachmentType(contentType));
    }

    @Test
    void resolvesMissingContentTypeAsOthers() {
        assertEquals(AttachmentType.OTHERS, attachmentService.resolveAttachmentType(null));
        assertEquals(AttachmentType.OTHERS, attachmentService.resolveAttachmentType("  "));
    }

    @Test
    void setsResolvedTypeWhenCreatingCompletedAttachment() {
        UUID uploaderId = UUID.randomUUID();
        User uploader = mock(User.class);
        when(uploader.getId()).thenReturn(uploaderId);
        when(userRepository.getReferenceById(uploaderId)).thenReturn(uploader);
        when(attachmentRepository.findByStorageKey("uploads/report.pdf"))
                .thenReturn(Optional.empty());
        when(attachmentRepository.saveAndFlush(any(Attachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Attachment created = attachmentService.createCompletedAttachment(
                "uploads/report.pdf",
                512,
                Map.of(
                        "filename", "report.pdf",
                        "contentType", "application/pdf"
                ),
                uploaderId
        );

        assertEquals(AttachmentType.PDF, created.getType());
    }
}
