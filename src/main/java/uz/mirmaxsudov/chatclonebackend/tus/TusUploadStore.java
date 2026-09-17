package uz.mirmaxsudov.chatclonebackend.tus;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TusUploadStore {
    private final Map<String, TusUpload> uploads = new ConcurrentHashMap<>();

    public TusUpload create(
            String id,
            String objectKey,
            long uploadLength,
            Map<String, String> metadata,
            UUID uploaderId
    ) {
        TusUpload upload = new TusUpload(id, objectKey, uploadLength, metadata, uploaderId);
        uploads.put(id, upload);
        return upload;
    }

    public Optional<TusUpload> findById(String id) {
        return Optional.ofNullable(uploads.get(id));
    }

    public TusUpload getRequired(String id) {
        TusUpload upload = uploads.get(id);
        if (upload == null)
            throw new TusProtocolException(HttpStatus.NOT_FOUND, "Upload not found: " + id);
        return upload;
    }

    public void remove(String id) {
        uploads.remove(id);
    }

    public boolean remove(String id, TusUpload upload) {
        return uploads.remove(id, upload);
    }

    public boolean contains(String id, TusUpload upload) {
        return uploads.get(id) == upload;
    }
}
