package uz.mirmaxsudov.chatclonebackend.model.response.chat;

import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;

import java.util.UUID;

public record RealtimeMessageEvent(
        String type,
        UUID chatId,
        ChatType chatType,
        MessageResponse message
) {
}
