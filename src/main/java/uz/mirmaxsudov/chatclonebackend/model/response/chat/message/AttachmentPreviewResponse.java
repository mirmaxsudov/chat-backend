package uz.mirmaxsudov.chatclonebackend.model.response.chat.message;

import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;

public record AttachmentPreviewResponse(
        PreviewStatus status,
        String url,
        String contentType,
        Long sizeBytes,
        Integer width,
        Integer height
) {
}
