package uz.mirmaxsudov.chatclonebackend.service.tus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.DownloadPayload;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.model.tus.UploadChunk;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentService;
import uz.mirmaxsudov.chatclonebackend.service.attachment.VideoThumbnailJobScheduler;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;
import uz.mirmaxsudov.chatclonebackend.tus.TusProtocolException;
import uz.mirmaxsudov.chatclonebackend.tus.TusUploadStore;

import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UploadService {
    private static final int MAX_METADATA_KEY_LENGTH = 128;
    private static final int MAX_METADATA_VALUE_LENGTH = 2048;
    private static final int MAX_FILE_NAME_LENGTH = 512;
    private static final int MAX_CONTENT_TYPE_LENGTH = 255;

    private final TusUploadStore tusUploadStore;
    private final StorageService storageService;
    private final TusProperties tusProperties;
    private final AttachmentService attachmentService;
    private final VideoThumbnailJobScheduler videoThumbnailJobScheduler;

    public TusUpload createUpload(long uploadLength, Map<String, String> metadata, UUID uploaderId) {
        if (uploadLength <= 0)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload-Length must be greater than zero");

        if (uploadLength > tusProperties.getMaxUploadSizeBytes())
            throw new TusProtocolException(HttpStatus.CONTENT_TOO_LARGE,
                    "Upload-Length exceeds max size: " + tusProperties.getMaxUploadSizeBytes());

        Map<String, String> normalizedMetadata = normalizeAndValidateMetadata(metadata);
        String id = UUID.randomUUID().toString();
        String objectKey = "uploads/" + id;
        TusUpload upload = tusUploadStore.create(
                id,
                objectKey,
                uploadLength,
                normalizedMetadata,
                uploaderId
        );

        log.info("Created TUS upload id={}, length={}", id, uploadLength);

        return upload;
    }

    public long appendChunk(
            String id,
            UUID uploaderId,
            long uploadOffset,
            long chunkSize,
            InputStream inputStream
    ) {
        if (chunkSize <= 0)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Chunk size must be greater than zero");

        TusUpload upload = getOwnedUpload(id, uploaderId);

        upload.lock();
        try {
            if (!tusUploadStore.contains(id, upload))
                throw new TusProtocolException(HttpStatus.NOT_FOUND, "Upload not found: " + id);

            if (upload.isCompleted())
                throw new TusProtocolException(HttpStatus.CONFLICT, "Upload is already completed");

            if (uploadOffset != upload.getOffset())
                throw new TusProtocolException(HttpStatus.CONFLICT,
                        "Invalid Upload-Offset. Expected " + upload.getOffset() + " but received " + uploadOffset);

            long nextOffset = upload.getOffset() + chunkSize;
            if (nextOffset > upload.getUploadLength())
                throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Chunk exceeds declared Upload-Length");

            String chunkObjectKey = "uploads/.tus/" + id + "/" + upload.getOffset();
            storageService.uploadObject(chunkObjectKey, inputStream, chunkSize, "application/offset+octet-stream", Map.of());

            UploadChunk chunk = new UploadChunk(chunkObjectKey, upload.getOffset(), chunkSize);
            if (nextOffset == upload.getUploadLength()) {
                completeUpload(upload, chunk);
            } else {
                upload.addChunk(chunk);
                upload.incrementOffset(chunkSize);
            }

            log.debug("Upload progress id={} offset={}/{}", upload.getId(), upload.getOffset(), upload.getUploadLength());

            return upload.getOffset();
        } finally {
            upload.unlock();
        }
    }

    public TusUpload getUpload(String id, UUID uploaderId) {
        return getOwnedUpload(id, uploaderId);
    }

    public void deleteUpload(String id, UUID uploaderId) {
        TusUpload upload = getOwnedUpload(id, uploaderId);

        upload.lock();
        try {
            if (upload.isCompleted())
                throw new TusProtocolException(
                        HttpStatus.CONFLICT,
                        "A completed attachment cannot be deleted through the TUS upload endpoint"
                );

            List<String> chunkObjectKeys = upload.getChunks().stream()
                    .map(UploadChunk::objectKey)
                    .toList();

            storageService.removeObjects(chunkObjectKeys);
            if (storageService.objectExists(upload.getObjectKey()))
                storageService.removeObject(upload.getObjectKey());

            tusUploadStore.remove(id);
            log.info("Deleted upload id={} and cleaned storage", id);
        } finally {
            upload.unlock();
        }
    }

    public DownloadPayload openDownload(String id, UUID uploaderId, String rangeHeader) {
        TusUpload upload = getOwnedUpload(id, uploaderId);

        if (!upload.isCompleted())
            throw new TusProtocolException(HttpStatus.CONFLICT, "Upload is not complete yet");

        long totalSize = storageService.statObject(upload.getObjectKey()).size();
        ByteRange range = parseRange(rangeHeader, totalSize);
        InputStream inputStream = storageService.openObject(upload.getObjectKey(), range.start(), range.partial() ? range.length() : null);

        String contentType = upload.getMetadata().getOrDefault("contentType", "application/octet-stream");
        String fileName = upload.getMetadata().get("filename");

        log.debug(
                "Opening upload download: id={}, partial={}, rangeStart={}, rangeEnd={}",
                id,
                range.partial(),
                range.start(),
                range.end()
        );

        return new DownloadPayload(
                inputStream,
                totalSize,
                range.length(),
                range.start(),
                range.end(),
                range.partial(),
                contentType,
                fileName
        );
    }

    private void completeUpload(TusUpload upload, UploadChunk finalChunk) {
        List<String> orderedChunks = Stream.concat(upload.getChunks().stream(), Stream.of(finalChunk))
                .sorted(Comparator.comparingLong(UploadChunk::offset))
                .map(UploadChunk::objectKey)
                .toList();

        storageService.composeObject(upload.getObjectKey(), orderedChunks);
        Map<String, String> attachmentMetadata = new HashMap<>(upload.getMetadata());

        if (isVideo(attachmentMetadata.get("contentType"))) {
            String thumbnailStorageKey = "thumbnails/" + upload.getId() + ".jpg";
            if (videoThumbnailJobScheduler.submit(
                    upload.getObjectKey(),
                    upload.getId(),
                    thumbnailStorageKey
            )) {
                attachmentMetadata.put(
                        AttachmentService.THUMBNAIL_STORAGE_KEY_METADATA,
                        thumbnailStorageKey
                );
            }
        }

        Attachment attachment = attachmentService.createCompletedAttachment(
                upload.getObjectKey(),
                upload.getUploadLength(),
                Map.copyOf(attachmentMetadata),
                upload.getUploaderId()
        );
        upload.addChunk(finalChunk);
        upload.incrementOffset(finalChunk.size());
        upload.markCompleted(attachment.getId());

        if (tusProperties.isChunkCleanupOnComplete()) {
            storageService.removeObjects(orderedChunks);
        }

        log.info(
                "Upload completed id={} attachmentId={} objectKey={}",
                upload.getId(),
                attachment.getId(),
                upload.getObjectKey()
        );
    }

    private boolean isVideo(String contentType) {
        return contentType != null
                && contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT).startsWith("video/");
    }

    private TusUpload getOwnedUpload(String id, UUID uploaderId) {
        TusUpload upload = tusUploadStore.getRequired(id);
        if (!upload.getUploaderId().equals(uploaderId))
            throw new TusProtocolException(HttpStatus.NOT_FOUND, "Upload not found: " + id);
        return upload;
    }

    private Map<String, String> normalizeAndValidateMetadata(Map<String, String> metadata) {
        Map<String, String> normalized = new HashMap<>(metadata == null ? Map.of() : metadata);
        if (normalized.containsKey(AttachmentService.THUMBNAIL_STORAGE_KEY_METADATA))
            throw new TusProtocolException(
                    HttpStatus.BAD_REQUEST,
                    "Upload metadata key is reserved: " + AttachmentService.THUMBNAIL_STORAGE_KEY_METADATA
            );
        if (!normalized.containsKey("contentType") && normalized.containsKey("filetype"))
            normalized.put("contentType", normalized.get("filetype"));

        normalized.forEach((key, value) -> {
            if (key.length() > MAX_METADATA_KEY_LENGTH)
                throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload metadata key is too long: " + key);
            if (value.length() > MAX_METADATA_VALUE_LENGTH)
                throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload metadata value is too long for key: " + key);
        });

        validateMetadataLength(normalized, "filename", MAX_FILE_NAME_LENGTH);
        validateMetadataLength(normalized, "contentType", MAX_CONTENT_TYPE_LENGTH);
        return Map.copyOf(normalized);
    }

    private void validateMetadataLength(Map<String, String> metadata, String key, int maxLength) {
        String value = metadata.get(key);
        if (value != null && value.length() > maxLength)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, key + " exceeds " + maxLength + " characters");
    }

    private ByteRange parseRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return new ByteRange(0, totalSize - 1, totalSize, false);
        }

        String[] tokens = getTokens(rangeHeader);

        long start;
        long end;

        try {
            if (tokens[0].isBlank()) {
                long suffixLength = Long.parseLong(tokens[1]);
                if (suffixLength <= 0) {
                    throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Invalid suffix range");
                }
                start = Math.max(totalSize - suffixLength, 0);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(tokens[0]);
                end = tokens[1].isBlank() ? totalSize - 1 : Long.parseLong(tokens[1]);
            }
        } catch (NumberFormatException ex) {
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Invalid numeric values in Range header");
        }

        if (start < 0 || end < start || start >= totalSize) {
            throw new TusProtocolException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Requested range is not satisfiable");
        }

        end = Math.min(end, totalSize - 1);
        return new ByteRange(start, end, end - start + 1, true);
    }

    private static String @NonNull [] getTokens(String rangeHeader) {
        if (!rangeHeader.startsWith("bytes="))
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Invalid Range header format");

        String raw = rangeHeader.substring("bytes=".length()).trim();

        if (raw.contains(","))
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Multiple ranges are not supported");

        String[] tokens = raw.split("-", 2);
        if (tokens.length != 2)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Invalid Range header value");

        return tokens;
    }

    private record ByteRange(long start, long end, long length, boolean partial) {
    }
}
