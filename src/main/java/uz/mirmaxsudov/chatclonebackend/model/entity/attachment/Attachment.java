package uz.mirmaxsudov.chatclonebackend.model.entity.attachment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachment_uploader", columnList = "uploaded_by_id")
})
public class Attachment extends BaseEntity {
    @Column(nullable = false, unique = true, length = 1024)
    private String storageKey;

    @Column(nullable = false, length = 512)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AttachmentType type;

    @Builder.Default
    @Column(length = 24)
    @Enumerated(EnumType.STRING)
    private PreviewStatus previewStatus = PreviewStatus.NOT_APPLICABLE;

    @Column(length = 1024)
    private String previewStorageKey;

    @Column(length = 255)
    private String previewContentType;

    private Long previewSizeBytes;
    private Integer previewWidth;
    private Integer previewHeight;

    @Column(length = 512)
    private String previewError;

    @Builder.Default
    private Integer previewAttempts = 0;

    private Instant previewUpdatedAt;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "metadata_value", length = 2048)
    @MapKeyColumn(name = "metadata_key", length = 128)
    @CollectionTable(name = "attachment_metadata", joinColumns = @JoinColumn(name = "attachment_id"))
    private Map<String, String> metadata = new HashMap<>();
}
