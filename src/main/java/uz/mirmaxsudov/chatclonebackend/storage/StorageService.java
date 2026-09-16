package uz.mirmaxsudov.chatclonebackend.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.config.minio.MinioProperties;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StorageService {
    private static final long MIN_COMPOSE_PART_SIZE = 5L * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void initBucket() {
        ensureBucketExists();
    }

    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
            );

            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                log.info("Created MinIO bucket '{}'", minioProperties.getBucket());
            }
        } catch (Exception e) {
            throw new StorageException("Failed to initialize MinIO bucket", e);
        }
    }

    public void uploadObject(String objectKey, InputStream inputStream, long size, String contentType, Map<String, String> metadata) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType);

            if (metadata != null && !metadata.isEmpty())
                builder.userMetadata(metadata);

            minioClient.putObject(builder.build());
            statObject(objectKey);
            log.debug("Storage object uploaded: objectKey={}, size={}", objectKey, size);
        } catch (Exception e) {
            throw new StorageException("Failed to upload object: " + objectKey, e);
        }
    }

    public void composeObject(String targetObjectKey, List<String> sourceObjectKeys) {
        if (sourceObjectKeys.isEmpty())
            throw new StorageException("Cannot compose an object without source chunks");

        try {
            List<StatObjectResponse> sourceStats = new ArrayList<>(sourceObjectKeys.size());
            for (String sourceObjectKey : sourceObjectKeys)
                sourceStats.add(statObject(sourceObjectKey));

            boolean canUseServerSideCompose = sourceObjectKeys.size() <= 10_000;
            for (int index = 0; index < sourceStats.size() - 1; index++) {
                if (sourceStats.get(index).size() < MIN_COMPOSE_PART_SIZE) {
                    canUseServerSideCompose = false;
                    break;
                }
            }

            if (!canUseServerSideCompose) {
                concatenateObjects(targetObjectKey, sourceObjectKeys, sourceStats);
                return;
            }

            List<ComposeSource> sources = sourceObjectKeys.stream()
                    .map(objectKey -> ComposeSource.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build())
                    .toList();

            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(targetObjectKey)
                            .sources(sources)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to compose object: " + targetObjectKey, e);
        }
    }

    private void concatenateObjects(
            String targetObjectKey,
            List<String> sourceObjectKeys,
            List<StatObjectResponse> sourceStats
    ) throws IOException {
        long totalSize = sourceStats.stream().mapToLong(StatObjectResponse::size).sum();

        try (InputStream chunks = new ObjectSequenceInputStream(sourceObjectKeys)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(targetObjectKey)
                            .stream(chunks, totalSize, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            log.debug(
                    "Storage object composed: objectKey={}, sourceCount={}",
                    targetObjectKey,
                    sourceObjectKeys.size()
            );
        } catch (StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to concatenate source chunks", exception);
        }
    }

    private final class ObjectSequenceInputStream extends InputStream {
        private final List<String> objectKeys;
        private int nextObjectIndex;
        private InputStream currentStream;

        private ObjectSequenceInputStream(List<String> objectKeys) {
            this.objectKeys = objectKeys;
        }

        @Override
        public int read() throws IOException {
            byte[] singleByte = new byte[1];
            int read = read(singleByte, 0, 1);
            return read == -1 ? -1 : Byte.toUnsignedInt(singleByte[0]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            while (true) {
                if (currentStream == null && !openNextStream())
                    return -1;

                int read = currentStream.read(bytes, offset, length);
                if (read != -1)
                    return read;

                currentStream.close();
                currentStream = null;
            }
        }

        private boolean openNextStream() {
            if (nextObjectIndex >= objectKeys.size())
                return false;

            currentStream = openObject(objectKeys.get(nextObjectIndex++), 0, null);
            return true;
        }

        @Override
        public void close() throws IOException {
            if (currentStream != null)
                currentStream.close();
        }
    }

    public InputStream openObject(String objectKey, long offset, Long length) {
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey);

            if (offset > 0) {
                builder.offset(offset);
            }
            if (length != null && length > 0) {
                builder.length(length);
            }

            return minioClient.getObject(builder.build());
        } catch (Exception e) {
            if (isObjectNotFound(e))
                throw new StorageObjectNotFoundException("Storage object not found: " + objectKey, e);
            throw new StorageException("Failed to read object: " + objectKey, e);
        }
    }

    public StatObjectResponse statObject(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            if (isObjectNotFound(e))
                throw new StorageObjectNotFoundException("Storage object not found: " + objectKey, e);
            throw new StorageException("Failed to stat object: " + objectKey, e);
        }
    }

    public void removeObject(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
            log.debug("Storage object removed: objectKey={}", objectKey);
        } catch (Exception e) {
            if (isObjectNotFound(e)) {
                log.info("Storage object '{}' was already absent during delete", objectKey);
                return;
            }
            throw new StorageException("Failed to delete object: " + objectKey, e);
        }
    }

    public void removeObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                removeObject(objectKey);
            } catch (StorageException ex) {
                log.warn("Failed to remove object '{}' during cleanup: {}", objectKey, ex.getMessage());
            }
        }
    }

    public boolean objectExists(String objectKey) {
        try {
            statObject(objectKey);
            return true;
        } catch (StorageObjectNotFoundException ex) {
            return false;
        }
    }

    private boolean isObjectNotFound(Exception exception) {
        if (exception instanceof ErrorResponseException errorResponseException) {
            String code = errorResponseException.errorResponse().code();
            return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
        }
        return false;
    }
}
