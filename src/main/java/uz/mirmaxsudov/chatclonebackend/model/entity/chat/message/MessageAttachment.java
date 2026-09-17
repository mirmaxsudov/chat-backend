package uz.mirmaxsudov.chatclonebackend.model.entity.chat.message;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_attachments", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_message_attachment",
                columnNames = {"message_id", "attachment_id"}
        )
}, indexes = {
        @Index(name = "idx_message_attachment_attachment", columnList = "attachment_id")
})
public class MessageAttachment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @Column(nullable = false)
    private int sortOrder;
}
