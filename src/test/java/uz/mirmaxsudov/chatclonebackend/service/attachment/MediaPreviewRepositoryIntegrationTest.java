package uz.mirmaxsudov.chatclonebackend.service.attachment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MediaPreviewRepositoryIntegrationTest {
    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void backfillsClaimsAndCompletesPreviewStateAtomically() {
        User uploader = saveUser();
        Attachment image = saveAttachment(uploader, AttachmentType.IMAGE, "image/jpeg");
        Attachment document = saveAttachment(uploader, AttachmentType.PDF, "application/pdf");
        Attachment legacyFailure = saveAttachment(uploader, AttachmentType.IMAGE, "image/jpeg");
        image.setPreviewStatus(null);
        image.setPreviewAttempts(null);
        document.setPreviewStatus(null);
        document.setPreviewAttempts(null);
        legacyFailure.setPreviewStatus(PreviewStatus.FAILED);
        legacyFailure.setPreviewAttempts(3);
        legacyFailure.setPreviewError("Media dimensions exceed the configured preview limit");
        attachmentRepository.saveAllAndFlush(List.of(image, document, legacyFailure));

        Instant now = Instant.parse("2026-09-18T10:15:30Z");
        List<AttachmentType> previewable = List.of(AttachmentType.IMAGE, AttachmentType.VIDEO);
        assertThat(attachmentRepository.initializeMissingPreviewableStatuses(
                previewable,
                PreviewStatus.PENDING,
                now
        )).isEqualTo(1);
        assertThat(attachmentRepository.initializeMissingNonPreviewableStatuses(
                previewable,
                PreviewStatus.NOT_APPLICABLE,
                now
        )).isEqualTo(1);
        assertThat(attachmentRepository.resetFailedPreviewsByError(
                PreviewStatus.FAILED,
                PreviewStatus.PENDING,
                "Media dimensions exceed the configured preview limit",
                now
        )).isEqualTo(1);

        Attachment requeued = attachmentRepository.findByIdAndDeletedFalse(legacyFailure.getId()).orElseThrow();
        assertThat(requeued.getPreviewStatus()).isEqualTo(PreviewStatus.PENDING);
        assertThat(requeued.getPreviewAttempts()).isZero();
        assertThat(requeued.getPreviewError()).isNull();

        assertThat(attachmentRepository.claimPreview(
                image.getId(),
                PreviewStatus.PENDING,
                PreviewStatus.PROCESSING,
                3,
                now
        )).isEqualTo(1);
        Attachment claimed = attachmentRepository.findByIdAndDeletedFalse(image.getId()).orElseThrow();
        assertThat(claimed.getPreviewStatus()).isEqualTo(PreviewStatus.PROCESSING);
        assertThat(claimed.getPreviewAttempts()).isEqualTo(1);

        assertThat(attachmentRepository.markPreviewReady(
                image.getId(),
                PreviewStatus.PROCESSING,
                PreviewStatus.READY,
                "previews/" + image.getId() + "/v1.jpg",
                "image/jpeg",
                42_000,
                640,
                427,
                now
        )).isEqualTo(1);

        Attachment ready = attachmentRepository.findByIdAndDeletedFalse(image.getId()).orElseThrow();
        assertThat(ready.getPreviewStatus()).isEqualTo(PreviewStatus.READY);
        assertThat(ready.getPreviewContentType()).isEqualTo("image/jpeg");
        assertThat(ready.getPreviewWidth()).isEqualTo(640);
        assertThat(attachmentRepository.findByIdAndDeletedFalse(document.getId()).orElseThrow().getPreviewStatus())
                .isEqualTo(PreviewStatus.NOT_APPLICABLE);
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .phoneNumber("+99896" + suffix)
                .username("preview." + suffix)
                .firstname("Preview")
                .lastname("Tester")
                .passwordHash("not-used-in-this-test")
                .build());
    }

    private Attachment saveAttachment(User uploader, AttachmentType type, String contentType) {
        return attachmentRepository.save(Attachment.builder()
                .storageKey("tests/" + UUID.randomUUID())
                .originalFileName("source")
                .contentType(contentType)
                .sizeBytes(100)
                .uploadedBy(uploader)
                .type(type)
                .build());
    }
}
