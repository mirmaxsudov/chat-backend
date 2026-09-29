package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomForbiddenException;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;
import uz.mirmaxsudov.chatclonebackend.repository.chat.message.MessageRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.MessageService;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;

    @Override
    public Message delete(UUID currentUserId, UUID chatId, UUID messageId) {
        Message message = messageRepository.findByIdAndChatIdAndDeletedFalseForUpdate(messageId, chatId)
                .orElseThrow(() -> new CustomNotFoundException("Message not found"));

        if (!message.getSender().getId().equals(currentUserId))
            throw new CustomForbiddenException("You can only delete your own messages");

        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        messageRepository.flush();
        return message;
    }
}
