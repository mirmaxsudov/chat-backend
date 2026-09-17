package uz.mirmaxsudov.chatclonebackend.model.response.chat.message;

public record AttachmentMessageResponse(
        String name,
        String contentType,
        long sizeBytes,
        String publicURL
) {
}