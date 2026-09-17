package uz.mirmaxsudov.chatclonebackend.controller.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.common.util.responseUtil.ResponseSuccessBuilder;
import uz.mirmaxsudov.chatclonebackend.model.request.chat.SendMessageRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageHistoryResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageResponse;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;

import java.util.UUID;

import static uz.mirmaxsudov.chatclonebackend.common.util.UserExtractor.userId;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(APIUtil.API_BASE_URL + "chats")
public class ChatMessageController {
    private final ChatService chatService;

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse message = chatService.sendMessage(
                userId(jwt),
                chatId,
                request
        );
        return ResponseEntity.ok(ResponseSuccessBuilder.success("Message sent", message));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponse<MessageHistoryResponse>> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatId,
            @RequestParam(required = false) @Positive(message = "beforeSeq must be greater than zero")
            Long beforeSeq,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size
    ) {
        MessageHistoryResponse messages = chatService.getMessages(
                userId(jwt),
                chatId,
                beforeSeq,
                size
        );
        return ResponseEntity.ok(ResponseSuccessBuilder.success("Messages retrieved", messages));
    }
}