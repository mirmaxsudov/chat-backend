package uz.mirmaxsudov.chatclonebackend.service.base.chat;

import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;

import java.util.UUID;

public interface MessageService {
    Message delete(UUID currentUserId, UUID chatId, UUID messageId);
}
