package uz.mirmaxsudov.chatclonebackend.storage;

import java.time.Instant;

public record StoredObject(String objectKey, Instant lastModified) {
}
