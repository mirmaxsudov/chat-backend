package uz.mirmaxsudov.chatclonebackend.model.response.chat;

import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.response.user.PublicUserResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatResponse(
        UUID id,
        ChatType type,
        PublicUserResponse peer,
        MessageResponse lastMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
