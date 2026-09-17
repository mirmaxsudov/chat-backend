package uz.mirmaxsudov.chatclonebackend.service.tus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;
import uz.mirmaxsudov.chatclonebackend.storage.StoredObject;
import uz.mirmaxsudov.chatclonebackend.tus.TusUploadStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "tus", name = "stale-chunk-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class StaleChunkCleanupService {
    static final String CHUNK_PREFIX = "uploads/.tus/";

    private final StorageService storageService;
    private final TusUploadStore tusUploadStore;
    private final TusProperties tusProperties;

    @Scheduled(
            fixedDelayString = "${tus.stale-chunk-cleanup-interval:PT1H}",
            initialDelayString = "${tus.stale-chunk-cleanup-initial-delay:PT1M}"
    )
    public void cleanupStaleChunks() {
        cleanupStaleChunks(Instant.now());
    }

    void cleanupStaleChunks(Instant now) {
        Instant cutoff = now.minus(tusProperties.getStaleChunkRetention());

        Map<String, List<StoredObject>> chunksByUpload = storageService.listObjects(CHUNK_PREFIX).stream()
                .filter(object -> object.objectKey().startsWith(CHUNK_PREFIX))
                .filter(object -> object.lastModified() != null)
                .filter(object -> uploadId(object.objectKey()) != null)
                .collect(Collectors.groupingBy(object -> uploadId(object.objectKey())));

        int removedChunkCount = 0;
        int removedUploadCount = 0;

        for (Map.Entry<String, List<StoredObject>> entry : chunksByUpload.entrySet()) {
            String uploadId = entry.getKey();
            if (uploadId == null || uploadId.isBlank())
                continue;

            List<StoredObject> chunks = entry.getValue();
            boolean inactiveBeforeCutoff = chunks.stream()
                    .allMatch(chunk -> chunk.lastModified().isBefore(cutoff));
            if (!inactiveBeforeCutoff)
                continue;

            TusUpload upload = tusUploadStore.findById(uploadId).orElse(null);
            if (upload == null) {
                storageService.removeObjects(chunkKeys(chunks));
                removedChunkCount += chunks.size();
                continue;
            }

            upload.lock();
            try {
                if (!tusUploadStore.contains(uploadId, upload) || !upload.getUpdatedAt().isBefore(cutoff))
                    continue;

                storageService.removeObjects(chunkKeys(chunks));
                removedChunkCount += chunks.size();

                if (!upload.isCompleted() && tusUploadStore.remove(uploadId, upload))
                    removedUploadCount++;
            } finally {
                upload.unlock();
            }
        }

        if (removedChunkCount > 0) {
            log.info(
                    "Removed {} stale TUS chunks and {} incomplete upload records older than {}",
                    removedChunkCount,
                    removedUploadCount,
                    tusProperties.getStaleChunkRetention()
            );
        }
    }

    private static List<String> chunkKeys(List<StoredObject> chunks) {
        return chunks.stream().map(StoredObject::objectKey).toList();
    }

    private static String uploadId(String objectKey) {
        String relativeKey = objectKey.substring(CHUNK_PREFIX.length());
        int separator = relativeKey.indexOf('/');
        return separator > 0 ? relativeKey.substring(0, separator) : null;
    }
}
