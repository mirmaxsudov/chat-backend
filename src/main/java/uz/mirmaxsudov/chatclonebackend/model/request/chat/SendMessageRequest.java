package uz.mirmaxsudov.chatclonebackend.model.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message text is required")
        @Size(max = 4096, message = "Message text must not exceed 4096 characters")
        String text
) {
}
