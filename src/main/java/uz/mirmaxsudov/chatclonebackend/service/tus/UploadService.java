package uz.mirmaxsudov.chatclonebackend.service.tus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.DownloadPayload;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.model.tus.UploadChunk;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;
import uz.mirmaxsudov.chatclonebackend.tus.TusProtocolException;
import uz.mirmaxsudov.chatclonebackend.tus.TusUploadStore;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UploadService {
    private final TusUploadStore tusUploadStore;
    private final StorageService storageService;
    private final TusProperties tusProperties;

    public TusUpload createUpload(long uploadLength, Map<String, String> metadata) {
        if (uploadLength <= 0)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload-Length must be greater than zero");

        if (uploadLength > tusProperties.getMaxUploadSizeBytes())
            throw new TusProtocolException(HttpStatus.CONTENT_TOO_LARGE,
                    "Upload-Length exceeds max size: " + tusProperties.getMaxUploadSizeBytes());

        String id = UUID.randomUUID().toString();
        String objectKey = "uploads/" + id;
        TusUpload upload = tusUploadStore.create(id, objectKey, uploadLength, metadata == null ? Map.of() : metadata);

        log.info("Created TUS upload id={}, length={}", id, uploadLength);

        return upload;
    }

    public long appendChunk(String id, long uploadOffset, long chunkSize, InputStream inputStream) {
        if (chunkSize <= 0)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Chunk size must be greater than zero");

        TusUpload upload = tusUploadStore.getRequired(id);

        upload.lock();
        try {
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

    public TusUpload getUpload(String id) {
        return tusUploadStore.getRequired(id);
    }

    public void deleteUpload(String id) {
        TusUpload upload = tusUploadStore.getRequired(id);

        upload.lock();
        try {
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

    public DownloadPayload openDownload(String id, String rangeHeader) {
        TusUpload upload = tusUploadStore.getRequired(id);

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
        upload.addChunk(finalChunk);
        upload.incrementOffset(finalChunk.size());
        upload.markCompleted();

        if (tusProperties.isChunkCleanupOnComplete()) {
            storageService.removeObjects(orderedChunks);
        }

        log.info("Upload completed id={} objectKey={}", upload.getId(), upload.getObjectKey());
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
