package uz.mirmaxsudov.chatclonebackend.repository.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByStorageKey(String storageKey);

    Optional<Attachment> findByIdAndDeletedFalse(UUID id);
}
