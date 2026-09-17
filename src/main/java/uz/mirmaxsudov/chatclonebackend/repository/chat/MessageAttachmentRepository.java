package uz.mirmaxsudov.chatclonebackend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;

import java.util.List;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
    List<MessageAttachment> findByMessageIdAndDeletedFalseOrderBySortOrder(UUID messageId);
}
