package uz.mirmaxsudov.chatclonebackend.event.chat;

import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.MessageAttachmentResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID chatId,
        ChatType chatType,
        UUID messageId,
        long sequence,
        UUID senderId,
        String text,
        LocalDateTime createdAt,
        List<UUID> recipientIds,
        List<MessageAttachmentResponse> attachments
) {
    public MessageCreatedEvent {
        recipientIds = List.copyOf(recipientIds);
        attachments = attachments == null ? Collections.emptyList() : List.copyOf(attachments);
    }
}
