package uz.mirmaxsudov.chatclonebackend.model.response.chat;

import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.MessageAttachmentResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        long seq,
        UUID senderId,
        String text,
        LocalDateTime createdAt,
        boolean mine,
        List<MessageAttachmentResponse> attachments
) {
    public MessageResponse {
        attachments = attachments == null ? Collections.emptyList() : List.copyOf(attachments);
    }
}
