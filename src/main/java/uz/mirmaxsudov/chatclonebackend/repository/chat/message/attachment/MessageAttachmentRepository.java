package uz.mirmaxsudov.chatclonebackend.repository.chat.message.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
    @Query("""
            select ma
            from MessageAttachment ma
            join fetch ma.attachment
            where ma.message.id in :messageIds
              and ma.deleted = false
            order by ma.message.id, ma.sortOrder
            """)
    List<MessageAttachment> findAllByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}
