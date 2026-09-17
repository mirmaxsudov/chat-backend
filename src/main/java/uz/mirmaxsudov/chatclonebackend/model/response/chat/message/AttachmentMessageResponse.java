package uz.mirmaxsudov.chatclonebackend.model.response.chat.message;

import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;

public record AttachmentMessageResponse(
        String name,
        String contentType,
        long sizeBytes,
        String publicURL,
        AttachmentType type
) {
}