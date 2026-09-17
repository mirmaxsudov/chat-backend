package uz.mirmaxsudov.chatclonebackend.service.base;

import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AttachmentService {
    List<Attachment> getAllByIds(Set<UUID> attachmentsIds, UUID currentUserId);
}
