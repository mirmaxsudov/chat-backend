package uz.mirmaxsudov.chatclonebackend.model.request.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendMessageRequest(
        @NotBlank(message = "Message text is required")
        @Size(max = 4096, message = "Message text must not exceed 4096 characters")
        String text,
        @Size(max = 10, message = "Message must not have more than 10 attachments")
        List<@Valid SendMessageAttachmentRequest> attachments
) {
    public SendMessageRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
