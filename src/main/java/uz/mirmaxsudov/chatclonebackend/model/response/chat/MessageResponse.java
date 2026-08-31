package uz.mirmaxsudov.chatclonebackend.model.response.chat;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        long seq,
        UUID senderId,
        String text,
        LocalDateTime createdAt,
        boolean mine
) {
}
