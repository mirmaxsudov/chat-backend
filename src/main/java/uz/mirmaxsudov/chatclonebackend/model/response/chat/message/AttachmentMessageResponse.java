package uz.mirmaxsudov.chatclonebackend.model.response.chat.message;

import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;

public record AttachmentMessageResponse(
        String name,
        String contentType,
        long sizeBytes,
        String publicURL,
        AttachmentType type,
        String thumbnailURL,
        AttachmentPreviewResponse preview
) {
    public AttachmentMessageResponse(
            String name,
            String contentType,
            long sizeBytes,
            String publicURL,
            AttachmentType type,
            String thumbnailURL
    ) {
        this(name, contentType, sizeBytes, publicURL, type, thumbnailURL, null);
    }
}
