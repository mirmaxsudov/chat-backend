package uz.mirmaxsudov.chatclonebackend.service.attachment;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import uz.mirmaxsudov.chatclonebackend.config.minio.VideoThumbnailProperties;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoThumbnailServiceTest {
    @Test
    void bundledFfmpegGeneratesAJpegWithoutAnExternalExecutable() throws Exception {
        byte[] video = createVideo();
        StorageService storageService = mock(StorageService.class);
        VideoThumbnailProperties properties = new VideoThumbnailProperties();
        properties.setMaxWidth(100);
        VideoThumbnailService service = new VideoThumbnailService(storageService, properties);
        AtomicReference<byte[]> uploadedThumbnail = new AtomicReference<>();

        when(storageService.openObject("uploads/video", 0, null))
                .thenReturn(new ByteArrayInputStream(video));
        doAnswer(invocation -> {
            try (InputStream stream = invocation.getArgument(1)) {
                uploadedThumbnail.set(stream.readAllBytes());
            }
            return null;
        }).when(storageService).uploadObject(
                eq("thumbnails/video.jpg"),
                any(InputStream.class),
                anyLong(),
                eq("image/jpeg"),
                any()
        );

        service.generateThumbnail("uploads/video", "video", "thumbnails/video.jpg");

        assertNotNull(uploadedThumbnail.get());
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(uploadedThumbnail.get()));
        assertNotNull(thumbnail);
        assertEquals(100, thumbnail.getWidth());
        assertTrue(thumbnail.getHeight() > 0);
    }

    private byte[] createVideo() throws Exception {
        Path videoFile = Files.createTempFile("thumbnail-test-", ".mp4");
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
