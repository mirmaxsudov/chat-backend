package uz.mirmaxsudov.chatclonebackend.model.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDmRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 64, message = "Username must not exceed 64 characters")
        String username
) {
}
