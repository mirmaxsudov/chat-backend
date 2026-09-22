package uz.mirmaxsudov.chatclonebackend.service.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.MediaPreviewProperties;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaPreviewService {
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final long MAX_VIDEO_FRAME_TIMESTAMP_MICROSECONDS = 5_000_000L;
    private static final int MAX_ERROR_LENGTH = 512;

    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final MediaPreviewProperties properties;
    private final Clock clock;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void generatePreview(UUID attachmentId) {
        Instant now = clock.instant();

        int claimed = attachmentRepository.claimPreview(
                attachmentId,
                PreviewStatus.PENDING,
                PreviewStatus.PROCESSING,
                properties.getMaxAttempts(),
                now
        );

        if (claimed == 0)
            return;

        Path inputFile = null;
        Path outputFile = null;

        String previewStorageKey;

        try {
            Attachment attachment = attachmentRepository.findByIdAndDeletedFalse(attachmentId)
                    .orElseThrow(() -> new IllegalStateException("Attachment no longer exists"));
            if (!isPreviewable(attachment.getType()))
                throw new IllegalStateException("Attachment type is not previewable");

            inputFile = Files.createTempFile("media-preview-input-", sourceSuffix(attachment));
            
            try (InputStream source = storageService.openObject(attachment.getStorageKey(), 0, null)) {
                Files.copy(source, inputFile, StandardCopyOption.REPLACE_EXISTING);
            }

            BufferedImage sourceImage = attachment.getType() == AttachmentType.VIDEO
                    ? extractVideoFrame(inputFile)
                    : readImage(inputFile);

            BufferedImage previewImage = resize(sourceImage);

            boolean preserveAlpha = attachment.getType() == AttachmentType.IMAGE
                    && previewImage.getColorModel().hasAlpha();

            String extension = preserveAlpha ? ".png" : ".jpg";

            String contentType = preserveAlpha ? PNG_CONTENT_TYPE : JPEG_CONTENT_TYPE;

            outputFile = Files.createTempFile("media-preview-output-", extension);

            if (preserveAlpha)
                writePng(previewImage, outputFile);
            else
                writeJpeg(previewImage, outputFile);

            previewStorageKey = "previews/" + attachmentId + "/v1" + extension;
            long previewSize = Files.size(outputFile);
            try (InputStream preview = Files.newInputStream(outputFile)) {
                storageService.uploadObject(
                        previewStorageKey,
                        preview,
                        previewSize,
                        contentType,
                        Map.of("sourceAttachmentId", attachmentId.toString())
                );
            }

            int updated = attachmentRepository.markPreviewReady(
                    attachmentId,
                    PreviewStatus.PROCESSING,
                    PreviewStatus.READY,
                    previewStorageKey,
                    contentType,
                    previewSize,
                    previewImage.getWidth(),
                    previewImage.getHeight(),
                    clock.instant()
            );

            if (updated == 0) {
                storageService.removeObject(previewStorageKey);
                log.warn("Discarded preview whose attachment state changed: attachmentId={}", attachmentId);
                return;
            }

            log.info(
                    "Generated media preview: attachmentId={}, type={}, objectKey={}, dimensions={}x{}, size={}",
                    attachmentId,
                    attachment.getType(),
                    previewStorageKey,
                    previewImage.getWidth(),
                    previewImage.getHeight(),
                    previewSize
            );
        } catch (Exception exception) {
            markFailure(attachmentId, exception);
            if (exception instanceof NonRetryablePreviewException) {
                log.warn("Media preview permanently rejected: attachmentId={}, reason={}",
                        attachmentId, exception.getMessage());
            } else {
                log.warn("Could not generate media preview for attachment {}: {}",
                        attachmentId, exception.getMessage(), exception);
            }
        } finally {
            deleteTemporaryFile(inputFile);
            deleteTemporaryFile(outputFile);
        }
    }

    private BufferedImage readImage(Path inputFile) throws Exception {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(inputFile.toFile())) {
            if (imageInput == null)
                throw new IllegalArgumentException("Unsupported or invalid image");

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext())
                throw new IllegalArgumentException("Unsupported or invalid image");

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                ImageReadParam readParameters = reader.getDefaultReadParam();
                int subsampling = calculateSubsampling(width, height);
                if (subsampling > 1)
                    readParameters.setSourceSubsampling(subsampling, subsampling, 0, 0);

                BufferedImage image = reader.read(0, readParameters);
                if (image == null)
                    throw new IllegalArgumentException("Image does not contain a decodable frame");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage extractVideoFrame(Path inputFile) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();

            long duration = grabber.getLengthInTime();
            if (duration > 0) {
                grabber.setTimestamp(Math.min(MAX_VIDEO_FRAME_TIMESTAMP_MICROSECONDS, duration / 10));
            }

            Frame frame = grabber.grabImage();
            if (frame == null && duration > 0) {
                grabber.setTimestamp(0);
                frame = grabber.grabImage();
            }
            if (frame == null)
                throw new IllegalArgumentException("Video does not contain a decodable image frame");

            BufferedImage image = converter.convert(frame);
            if (image == null)
                throw new IllegalArgumentException("Could not convert decoded video frame");
            validateDimensions(image.getWidth(), image.getHeight());
            return image;
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxSourcePixels())
            throw new NonRetryablePreviewException(
                    "Media dimensions " + width + "x" + height
                            + " exceed the configured source limit of "
                            + properties.getMaxSourcePixels() + " pixels"
            );
    }

    private int calculateSubsampling(int width, int height) {
        double widthRatio = (double) width / properties.getMaxWidth();
        double heightRatio = (double) height / properties.getMaxHeight();
        return Math.max(1, (int) Math.floor(Math.max(widthRatio, heightRatio)));
    }

    private BufferedImage resize(BufferedImage source) {
        double scale = Math.min(
                1.0,
                Math.min(
                        (double) properties.getMaxWidth() / source.getWidth(),
                        (double) properties.getMaxHeight() / source.getHeight()
                )
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        boolean alpha = source.getColorModel().hasAlpha();
        int imageType = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D graphics = resized.createGraphics();

        try {
            if (alpha) {
                graphics.setComposite(AlphaComposite.Src);
            } else {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private void writeJpeg(BufferedImage image, Path outputFile) throws Exception {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputFile.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(properties.getJpegQuality());
            if (parameters.canWriteProgressive())
                parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(rgb, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private void writePng(BufferedImage image, Path outputFile) throws Exception {
        if (!ImageIO.write(image, "png", outputFile.toFile()))
            throw new IllegalStateException("No PNG writer is available");
    }

    private void markFailure(UUID attachmentId, Exception exception) {
        try {
            Attachment attachment = attachmentRepository.findByIdAndDeletedFalse(attachmentId).orElse(null);
            if (attachment == null)
                return;

            int attempts = attachment.getPreviewAttempts() == null ? 0 : attachment.getPreviewAttempts();
            PreviewStatus nextStatus = exception instanceof NonRetryablePreviewException
                    ? PreviewStatus.FAILED
                    : attempts < properties.getMaxAttempts()
                    ? PreviewStatus.PENDING
                    : PreviewStatus.FAILED;
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            attachmentRepository.markPreviewFailure(
                    attachmentId,
                    PreviewStatus.PROCESSING,
                    nextStatus,
                    message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)),
                    clock.instant()
            );
        } catch (RuntimeException persistenceException) {
            log.error("Could not persist media preview failure: attachmentId={}", attachmentId, persistenceException);
        }
    }

    private boolean isPreviewable(AttachmentType type) {
        return type == AttachmentType.IMAGE || type == AttachmentType.VIDEO;
    }

    private String sourceSuffix(Attachment attachment) {
        return attachment.getType() == AttachmentType.VIDEO ? ".video" : ".image";
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null)
            return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception exception) {
            log.debug("Could not delete media preview temporary file {}", path, exception);
        }
    }

    private static final class NonRetryablePreviewException extends IllegalArgumentException {
        private NonRetryablePreviewException(String message) {
            super(message);
        }
    }
}
