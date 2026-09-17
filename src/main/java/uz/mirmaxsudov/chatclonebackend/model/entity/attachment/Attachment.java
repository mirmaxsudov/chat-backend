package uz.mirmaxsudov.chatclonebackend.model.entity.attachment;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

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

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "metadata_value", length = 2048)
    @MapKeyColumn(name = "metadata_key", length = 128)
    @CollectionTable(name = "attachment_metadata", joinColumns = @JoinColumn(name = "attachment_id"))
    private Map<String, String> metadata = new HashMap<>();
}
