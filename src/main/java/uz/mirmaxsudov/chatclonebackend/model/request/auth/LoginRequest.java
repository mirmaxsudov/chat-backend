package uz.mirmaxsudov.chatclonebackend.model.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Phone number is required")
        @Size(max = 32, message = "Phone number is too long")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password
) {
}
