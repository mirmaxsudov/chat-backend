package uz.mirmaxsudov.chatclonebackend.controller.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.common.util.responseUtil.ResponseSuccessBuilder;
import uz.mirmaxsudov.chatclonebackend.model.request.chat.CreateDmRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiPaginateResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.ChatResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageHistoryResponse;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;

import java.util.List;
import java.util.UUID;

import static uz.mirmaxsudov.chatclonebackend.common.util.UserExtractor.userId;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(APIUtil.API_BASE_URL + "chats")
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/dm")
    public ResponseEntity<ApiResponse<ChatResponse>> createOrGetDm(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDmRequest request
    ) {
        ChatResponse chat = chatService.createOrGetDm(userId(jwt), request.username());
        return ResponseEntity.ok(ResponseSuccessBuilder.success("Direct chat retrieved", chat));
    }

    @PostMapping("/saved")
    public ResponseEntity<ApiResponse<ChatResponse>> createOrGetSavedChat(
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatResponse chat = chatService.createOrGetSavedChat(userId(jwt));
        return ResponseEntity.ok(ResponseSuccessBuilder.success("Saved messages chat retrieved", chat));
    }

    @GetMapping
    public ResponseEntity<ApiPaginateResponse<List<ChatResponse>>> getChats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 50, message = "Size must not exceed 50")
            int size
    ) {
        Page<ChatResponse> chats = chatService.getChats(userId(jwt), page, size);
        return ResponseEntity.ok(ApiPaginateResponse.<List<ChatResponse>>builder()
                .success(true)
                .message("Chats retrieved")
                .results(chats.getContent())
                .total((int) Math.min(chats.getTotalElements(), Integer.MAX_VALUE))
                .page(chats.getNumber())
                .size(chats.getSize())
                .hasPrev(chats.hasPrevious())
                .hasNext(chats.hasNext())
                .build());
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ApiResponse<ChatResponse>> getChat(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatId
    ) {
        return ResponseEntity.ok(ResponseSuccessBuilder.success(
                "Chat retrieved",
                chatService.getChat(userId(jwt), chatId)
        ));
    }
}