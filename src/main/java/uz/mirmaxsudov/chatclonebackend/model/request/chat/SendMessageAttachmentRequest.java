package uz.mirmaxsudov.chatclonebackend.model.request.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record SendMessageAttachmentRequest(
        @NotNull(message = "Attachment ID is required")
        UUID id,
        @PositiveOrZero(message = "Sort order must be zero or a positive integer")
        int sortOrder
) {
}
