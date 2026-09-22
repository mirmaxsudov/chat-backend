package uz.mirmaxsudov.chatclonebackend.service.attachment;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.mirmaxsudov.chatclonebackend.config.minio.MediaPreviewProperties;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaPreviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T10:15:30Z");

    private StorageService storageService;
    private AttachmentRepository attachmentRepository;
    private MediaPreviewProperties properties;
    private MediaPreviewService service;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        attachmentRepository = mock(AttachmentRepository.class);
        properties = new MediaPreviewProperties();
        service = new MediaPreviewService(
                storageService,
                attachmentRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void resizesAndCompressesOpaqueImageAsJpeg() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        byte[] source = imageBytes(1200, 800, BufferedImage.TYPE_INT_RGB, "jpg");
        AtomicReference<byte[]> uploaded = prepare(attachmentId, AttachmentType.IMAGE, source);

        service.generatePreview(attachmentId);

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(uploaded.get()));
        assertThat(preview.getWidth()).isEqualTo(640);
        assertThat(preview.getHeight()).isEqualTo(427);
        verify(storageService).uploadObject(
                eq("previews/" + attachmentId + "/v1.jpg"),
                any(InputStream.class),
                anyLong(),
                eq("image/jpeg"),
                eq(Map.of("sourceAttachmentId", attachmentId.toString()))
        );
        verifyReady(attachmentId, "image/jpeg", 640, 427);
    }

    @Test
    void preservesTransparencyInResizedPngPreview() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        properties.setMaxWidth(100);
        properties.setMaxHeight(100);
        byte[] source = imageBytes(200, 400, BufferedImage.TYPE_INT_ARGB, "png");
        AtomicReference<byte[]> uploaded = prepare(attachmentId, AttachmentType.IMAGE, source);

        service.generatePreview(attachmentId);

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(uploaded.get()));
        assertThat(preview.getWidth()).isEqualTo(50);
        assertThat(preview.getHeight()).isEqualTo(100);
        assertThat(preview.getColorModel().hasAlpha()).isTrue();
        verifyReady(attachmentId, "image/png", 50, 100);
    }

    @Test
    void subsamplesHighResolutionImageBeforeCreatingPreview() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        properties.setMaxWidth(100);
        properties.setMaxHeight(100);
        byte[] source = imageBytes(7000, 6000, BufferedImage.TYPE_BYTE_BINARY, "png");
        AtomicReference<byte[]> uploaded = prepare(attachmentId, AttachmentType.IMAGE, source);

        service.generatePreview(attachmentId);

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(uploaded.get()));
        assertThat(preview.getWidth()).isEqualTo(100);
        assertThat(preview.getHeight()).isEqualTo(86);
        verifyReady(attachmentId, "image/jpeg", 100, 86);
    }

    @Test
    void permanentlyRejectsImageAboveHardSourceLimit() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        properties.setMaxSourcePixels(100);
        byte[] source = imageBytes(20, 20, BufferedImage.TYPE_INT_RGB, "png");
        prepare(attachmentId, AttachmentType.IMAGE, source);

        service.generatePreview(attachmentId);

        verify(storageService, never()).uploadObject(any(), any(), anyLong(), any(), any());
        verify(attachmentRepository).markPreviewFailure(
                eq(attachmentId),
                eq(PreviewStatus.PROCESSING),
                eq(PreviewStatus.FAILED),
                eq("Media dimensions 20x20 exceed the configured source limit of 100 pixels"),
                eq(NOW)
        );
    }

    @Test
    void extractsAndMinimizesOnlyTheVideoPosterFrame() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        properties.setMaxWidth(100);
        byte[] video = createVideo();
        AtomicReference<byte[]> uploaded = prepare(attachmentId, AttachmentType.VIDEO, video);

        service.generatePreview(attachmentId);

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(uploaded.get()));
        assertThat(preview.getWidth()).isEqualTo(100);
        assertThat(preview.getHeight()).isGreaterThan(0);
        verify(storageService).openObject("uploads/" + attachmentId, 0, null);
        verifyReady(attachmentId, "image/jpeg", 100, preview.getHeight());
    }

    private AtomicReference<byte[]> prepare(UUID attachmentId, AttachmentType type, byte[] source) throws Exception {
        Attachment attachment = Attachment.builder()
                .storageKey("uploads/" + attachmentId)
                .originalFileName("source")
                .contentType(type == AttachmentType.VIDEO ? "video/mp4" : "image/jpeg")
                .sizeBytes(source.length)
                .type(type)
                .previewStatus(PreviewStatus.PROCESSING)
                .previewAttempts(1)
                .build();
        attachment.setId(attachmentId);
        when(attachmentRepository.claimPreview(
                eq(attachmentId),
                eq(PreviewStatus.PENDING),
                eq(PreviewStatus.PROCESSING),
                anyInt(),
                any()
        )).thenReturn(1);
        when(attachmentRepository.findByIdAndDeletedFalse(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.markPreviewReady(
                eq(attachmentId),
                eq(PreviewStatus.PROCESSING),
                eq(PreviewStatus.READY),
                any(),
                any(),
                anyLong(),
                anyInt(),
                anyInt(),
                any()
        )).thenReturn(1);
        when(storageService.openObject(attachment.getStorageKey(), 0, null))
                .thenReturn(new ByteArrayInputStream(source));

        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        doAnswer(invocation -> {
            try (InputStream stream = invocation.getArgument(1)) {
                uploaded.set(stream.readAllBytes());
            }
            return null;
        }).when(storageService).uploadObject(any(), any(InputStream.class), anyLong(), any(), any());
        return uploaded;
    }

    private void verifyReady(UUID attachmentId, String contentType, int width, int height) {
        ArgumentCaptor<Long> size = ArgumentCaptor.forClass(Long.class);
        verify(attachmentRepository).markPreviewReady(
                eq(attachmentId),
                eq(PreviewStatus.PROCESSING),
                eq(PreviewStatus.READY),
                any(),
                eq(contentType),
                size.capture(),
                eq(width),
                eq(height),
                eq(NOW)
        );
        assertThat(size.getValue()).isPositive();
    }

    private byte[] imageBytes(int width, int height, int type, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 100, 200, type == BufferedImage.TYPE_INT_ARGB ? 120 : 255));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] createVideo() throws Exception {
        Path videoFile = Files.createTempFile("media-preview-test-", ".mp4");
        try {
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(videoFile.toFile(), 320, 180);
                 Java2DFrameConverter converter = new Java2DFrameConverter()) {
                recorder.setFormat("mp4");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.setFrameRate(2);
                recorder.start();
                recorder.record(converter.convert(frame(Color.BLUE)));
                recorder.record(converter.convert(frame(Color.ORANGE)));
                recorder.stop();
            }
            return Files.readAllBytes(videoFile);
        } finally {
            Files.deleteIfExists(videoFile);
        }
    }

    private BufferedImage frame(Color color) {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
