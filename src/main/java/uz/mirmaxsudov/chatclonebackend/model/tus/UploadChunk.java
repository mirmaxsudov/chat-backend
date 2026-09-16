package uz.mirmaxsudov.chatclonebackend.model.tus;

public record UploadChunk(String objectKey, long offset, long size) {
}