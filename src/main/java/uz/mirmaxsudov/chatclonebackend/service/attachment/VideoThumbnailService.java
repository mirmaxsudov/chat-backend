package uz.mirmaxsudov.chatclonebackend.service.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.VideoThumbnailProperties;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VideoThumbnailService {
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    private static final long MAX_THUMBNAIL_TIMESTAMP_MICROSECONDS = 5_000_000L;

    private final StorageService storageService;
    private final VideoThumbnailProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void generateThumbnail(
            String sourceStorageKey,
            String sourceIdentifier,
            String thumbnailStorageKey
    ) {
        Path inputFile = null;
        Path outputFile = null;

        try {
            inputFile = Files.createTempFile("video-thumbnail-input-", ".video");
            outputFile = Files.createTempFile("video-thumbnail-output-", ".jpg");

            try (InputStream video = storageService.openObject(sourceStorageKey, 0, null)) {
                Files.copy(video, inputFile, StandardCopyOption.REPLACE_EXISTING);
            }

            writeThumbnail(inputFile, outputFile);

            try (InputStream thumbnail = Files.newInputStream(outputFile)) {
                storageService.uploadObject(
                        thumbnailStorageKey,
                        thumbnail,
                        Files.size(outputFile),
                        THUMBNAIL_CONTENT_TYPE,
                        Map.of("sourceStorageKey", sourceStorageKey)
                );
            }

            log.info("Generated video thumbnail: attachment={}, objectKey={}", sourceIdentifier, thumbnailStorageKey);
        } catch (Exception exception) {
            log.warn(
                    "Could not generate thumbnail for video attachment {}: {}",
                    sourceIdentifier,
                    exception.getMessage(),
                    exception
            );
        } finally {
            deleteTemporaryFile(inputFile);
            deleteTemporaryFile(outputFile);
        }
    }

    private void writeThumbnail(Path inputFile, Path outputFile) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();

            long duration = grabber.getLengthInTime();
            if (duration > 0) {
                long timestamp = Math.min(MAX_THUMBNAIL_TIMESTAMP_MICROSECONDS, duration / 10);
                grabber.setTimestamp(timestamp);
            }

            Frame frame = grabber.grabImage();
            if (frame == null && duration > 0) {
                grabber.setTimestamp(0);
                frame = grabber.grabImage();
            }
            if (frame == null)
                throw new IllegalStateException("Video does not contain a decodable image frame");

            BufferedImage source = converter.convert(frame);
            if (source == null)
                throw new IllegalStateException("Could not convert decoded video frame to an image");

            BufferedImage thumbnail = scaleToMaxWidth(source, properties.getMaxWidth());
            if (!ImageIO.write(thumbnail, "jpg", outputFile.toFile()))
                throw new IllegalStateException("No JPEG image writer is available");
        }
    }

    private BufferedImage scaleToMaxWidth(BufferedImage source, int maxWidth) {
        if (maxWidth <= 0 || source.getWidth() <= maxWidth)
            return source;

        int targetHeight = Math.max(1, (int) Math.round(
                (double) source.getHeight() * maxWidth / source.getWidth()
        ));
        BufferedImage scaled = new BufferedImage(maxWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, maxWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null)
            return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception exception) {
            log.debug("Could not delete thumbnail temporary file {}", path, exception);
        }
    }
}
