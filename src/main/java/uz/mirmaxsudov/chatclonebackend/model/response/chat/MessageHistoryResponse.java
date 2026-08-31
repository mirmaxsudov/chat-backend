package uz.mirmaxsudov.chatclonebackend.model.response.chat;

import java.util.List;

public record MessageHistoryResponse(
        List<MessageResponse> messages,
        Long nextBeforeSeq,
        boolean hasMore
) {
}
