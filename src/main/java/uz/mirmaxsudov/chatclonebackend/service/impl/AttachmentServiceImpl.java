package uz.mirmaxsudov.chatclonebackend.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.AttachmentService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {
    private final AttachmentRepository attachmentRepository;

    @Override
    public List<Attachment> getAllByIds(Set<UUID> attachmentsIds, UUID currentUserId) {
        return attachmentRepository.findAllByIdInAndUploadedByIdAndDeletedFalse(attachmentsIds, currentUserId);
    }
}
