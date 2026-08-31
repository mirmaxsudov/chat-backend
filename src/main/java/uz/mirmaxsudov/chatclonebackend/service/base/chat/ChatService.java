package uz.mirmaxsudov.chatclonebackend.service.base.chat;

import org.springframework.data.domain.Page;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.ChatResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageHistoryResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageResponse;

import java.util.UUID;

public interface ChatService {
    ChatResponse createOrGetDm(UUID currentUserId, String username);

    ChatResponse createOrGetSavedChat(UUID currentUserId);

    Page<ChatResponse> getChats(UUID currentUserId, int page, int size);

    ChatResponse getChat(UUID currentUserId, UUID chatId);

    MessageHistoryResponse getMessages(UUID currentUserId, UUID chatId, Long beforeSeq, int size);

    MessageResponse sendMessage(UUID currentUserId, UUID chatId, String text);
}
