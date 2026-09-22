package uz.mirmaxsudov.chatclonebackend.repository.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByStorageKey(String storageKey);

    Optional<Attachment> findByIdAndDeletedFalse(UUID id);

    List<Attachment> findAllByIdInAndUploadedByIdAndDeletedFalse(Set<UUID> attachmentsIds, UUID currentUserId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :processing,
                a.previewAttempts = coalesce(a.previewAttempts, 0) + 1,
                a.previewUpdatedAt = :updatedAt,
                a.previewError = null
            where a.id = :attachmentId
              and a.deleted = false
              and a.previewStatus = :pending
              and coalesce(a.previewAttempts, 0) < :maxAttempts
            """)
    int claimPreview(
            @Param("attachmentId") UUID attachmentId,
            @Param("pending") PreviewStatus pending,
            @Param("processing") PreviewStatus processing,
            @Param("maxAttempts") int maxAttempts,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :ready,
                a.previewStorageKey = :storageKey,
                a.previewContentType = :contentType,
                a.previewSizeBytes = :sizeBytes,
                a.previewWidth = :width,
                a.previewHeight = :height,
                a.previewUpdatedAt = :updatedAt,
                a.previewError = null
            where a.id = :attachmentId
              and a.deleted = false
              and a.previewStatus = :processing
            """)
    int markPreviewReady(
            @Param("attachmentId") UUID attachmentId,
            @Param("processing") PreviewStatus processing,
            @Param("ready") PreviewStatus ready,
            @Param("storageKey") String storageKey,
            @Param("contentType") String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("width") int width,
            @Param("height") int height,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :status,
                a.previewUpdatedAt = :updatedAt,
                a.previewError = :error
            where a.id = :attachmentId
              and a.deleted = false
              and a.previewStatus = :processing
            """)
    int markPreviewFailure(
            @Param("attachmentId") UUID attachmentId,
            @Param("processing") PreviewStatus processing,
            @Param("status") PreviewStatus status,
            @Param("error") String error,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :pending,
                a.previewUpdatedAt = :updatedAt
            where a.deleted = false
              and a.previewStatus = :processing
              and a.previewUpdatedAt < :staleBefore
              and coalesce(a.previewAttempts, 0) < :maxAttempts
            """)
    int resetStalePreviews(
            @Param("processing") PreviewStatus processing,
            @Param("pending") PreviewStatus pending,
            @Param("staleBefore") Instant staleBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :failed,
                a.previewUpdatedAt = :updatedAt,
                a.previewError = 'Preview processing timed out'
            where a.deleted = false
              and a.previewStatus = :processing
              and a.previewUpdatedAt < :staleBefore
              and coalesce(a.previewAttempts, 0) >= :maxAttempts
            """)
    int failExhaustedStalePreviews(
            @Param("processing") PreviewStatus processing,
            @Param("failed") PreviewStatus failed,
            @Param("staleBefore") Instant staleBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :pending,
                a.previewAttempts = 0,
                a.previewUpdatedAt = :updatedAt
            where a.deleted = false
              and a.previewStatus is null
              and a.type in :previewableTypes
            """)
    int initializeMissingPreviewableStatuses(
            @Param("previewableTypes") Collection<uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType> previewableTypes,
            @Param("pending") PreviewStatus pending,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :notApplicable,
                a.previewAttempts = 0,
                a.previewUpdatedAt = :updatedAt
            where a.deleted = false
              and a.previewStatus is null
              and a.type not in :previewableTypes
            """)
    int initializeMissingNonPreviewableStatuses(
            @Param("previewableTypes") Collection<uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType> previewableTypes,
            @Param("notApplicable") PreviewStatus notApplicable,
            @Param("updatedAt") Instant updatedAt
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Attachment a
            set a.previewStatus = :pending,
                a.previewAttempts = 0,
                a.previewUpdatedAt = :updatedAt,
                a.previewError = null
            where a.deleted = false
              and a.previewStatus = :failed
              and a.previewError = :legacyError
            """)
    int resetFailedPreviewsByError(
            @Param("failed") PreviewStatus failed,
            @Param("pending") PreviewStatus pending,
            @Param("legacyError") String legacyError,
            @Param("updatedAt") Instant updatedAt
    );

    List<Attachment> findTop100ByPreviewStatusAndDeletedFalseOrderByCreatedAtAsc(PreviewStatus status);
}
