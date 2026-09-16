package uz.mirmaxsudov.chatclonebackend.model.tus;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class TusUpload {
    private final String id;
    private final String objectKey;
    private final long uploadLength;
    private final Map<String, String> metadata;
    private final List<UploadChunk> chunks = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private long offset;
    private boolean completed;
    private final Instant createdAt;
    private Instant updatedAt;

    public TusUpload(String id, String objectKey, long uploadLength, Map<String, String> metadata) {
        this.id = id;
        this.objectKey = objectKey;
        this.uploadLength = uploadLength;
        this.metadata = metadata;
        this.offset = 0L;
        this.completed = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void addChunk(UploadChunk chunk) {
        chunks.add(chunk);
        updatedAt = Instant.now();
    }

    public void incrementOffset(long chunkSize) {
        offset += chunkSize;
        updatedAt = Instant.now();
    }

    public void markCompleted() {
        completed = true;
        updatedAt = Instant.now();
    }
}
