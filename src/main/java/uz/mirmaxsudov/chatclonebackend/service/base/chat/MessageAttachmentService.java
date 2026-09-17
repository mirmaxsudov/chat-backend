package uz.mirmaxsudov.chatclonebackend.service.base.chat;

import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;
import uz.mirmaxsudov.chatclonebackend.model.request.chat.SendMessageAttachmentRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MessageAttachmentService {
    List<MessageAttachment> createAttachments(
            Message message,
            List<SendMessageAttachmentRequest> attachments,
            UUID currentUserId
    );

    Map<UUID, List<MessageAttachment>> getAttachmentsByMessageIds(Collection<UUID> messageIds);
}
