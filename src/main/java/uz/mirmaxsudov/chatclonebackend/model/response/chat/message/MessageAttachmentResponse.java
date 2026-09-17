package uz.mirmaxsudov.chatclonebackend.model.response.chat.message;

public record MessageAttachmentResponse(
        int sortOrder,
        AttachmentMessageResponse attachment
) {
}