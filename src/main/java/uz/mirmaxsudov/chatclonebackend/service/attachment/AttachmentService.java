package uz.mirmaxsudov.chatclonebackend.service.attachment;

import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.exceptions.AttachmentRangeNotSatisfiableException;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.response.attachment.AttachmentClientResponse;
import uz.mirmaxsudov.chatclonebackend.repository.attachment.AttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.storage.StorageService;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private final StorageService storageService;

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public Attachment createCompletedAttachment(
            String storageKey,
            long sizeBytes,
            Map<String, String> metadata,
            UUID uploaderId
    ) {
        return attachmentRepository.findByStorageKey(storageKey)
                .orElseGet(() -> create(storageKey, sizeBytes, metadata, uploaderId));
    }

    private Attachment create(
            String storageKey,
            long sizeBytes,
            Map<String, String> metadata,
            UUID uploaderId
    ) {
        User uploader = userRepository.getReferenceById(uploaderId);
        String originalFileName = valueOrDefault(metadata, "filename", storageKey.substring(storageKey.lastIndexOf('/') + 1));
        String contentType = valueOrDefault(metadata, "contentType", DEFAULT_CONTENT_TYPE);

        log.info("Creating attachment with storageKey: {}, originalFileName: {}, contentType: {}, sizeBytes: {}, uploadedBy: {}",
                storageKey, originalFileName, contentType, sizeBytes, uploader.getId());

        return attachmentRepository.saveAndFlush(Attachment.builder()
                .storageKey(storageKey)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .type(resolveAttachmentType(contentType))
                .sizeBytes(sizeBytes)
                .uploadedBy(uploader)
                .metadata(new HashMap<>(metadata))
                .build());
    }

    public AttachmentType resolveAttachmentType(String contentType) {
        if (contentType == null || contentType.isBlank())
            return AttachmentType.OTHERS;

        String normalized = contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalized.startsWith("image/"))
            return AttachmentType.IMAGE;
        if (normalized.startsWith("video/"))
            return AttachmentType.VIDEO;
        if (normalized.startsWith("audio/"))
            return AttachmentType.AUDIO;
        if (normalized.equals("application/pdf"))
            return AttachmentType.PDF;
        if (isExcelContentType(normalized))
            return AttachmentType.EXCEL;
        if (isPowerPointContentType(normalized))
            return AttachmentType.PPT;

        return AttachmentType.OTHERS;
    }

    private boolean isExcelContentType(String contentType) {
        return contentType.equals("text/csv")
                || contentType.startsWith("application/vnd.ms-excel")
                || contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml")
                || contentType.equals("application/vnd.oasis.opendocument.spreadsheet");
    }

    private boolean isPowerPointContentType(String contentType) {
        return contentType.startsWith("application/vnd.ms-powerpoint")
                || contentType.startsWith("application/vnd.openxmlformats-officedocument.presentationml")
                || contentType.equals("application/vnd.oasis.opendocument.presentation");
    }


    private String valueOrDefault(Map<String, String> metadata, String key, String defaultValue) {
        String value = metadata.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public AttachmentClientResponse getClientAttachment(UUID id) {
        return getClientAttachment(id, null, true);
    }

    public AttachmentClientResponse getClientAttachment(
            UUID id,
            String rangeHeader,
            boolean includeBody
    ) {
        Attachment attachment = attachmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomNotFoundException("Attachment not found with id: " + id));

        StatObjectResponse stat = storageService.statObject(attachment.getStorageKey());
        ByteRange range = parseRange(rangeHeader, stat.size());
        String contentType = normalizeContentType(stat.contentType());

        return new AttachmentClientResponse(
                includeBody
                        ? storageService.openObject(
                        attachment.getStorageKey(),
                        range.start(),
                        range.partial() ? range.length() : null
                )
                        : null,
                stat.size(),
                range.length(),
                range.start(),
                range.end(),
                range.partial(),
                contentType,
                attachment.getOriginalFileName()
        );
    }

    private ByteRange parseRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank())
            return new ByteRange(0, totalSize - 1, totalSize, false);

        if (totalSize <= 0
                || !rangeHeader.regionMatches(true, 0, "bytes=", 0, "bytes=".length()))
            throw new AttachmentRangeNotSatisfiableException(totalSize);

        String rawRange = rangeHeader.substring("bytes=".length()).trim();
        if (rawRange.isBlank() || rawRange.contains(","))
            throw new AttachmentRangeNotSatisfiableException(totalSize);

        String[] boundaries = rawRange.split("-", 2);
        if (boundaries.length != 2)
            throw new AttachmentRangeNotSatisfiableException(totalSize);

        try {
            long start;
            long end;

            if (boundaries[0].isBlank()) {
                long suffixLength = Long.parseLong(boundaries[1]);
                if (suffixLength <= 0)
                    throw new AttachmentRangeNotSatisfiableException(totalSize);

                start = Math.max(totalSize - suffixLength, 0);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(boundaries[0]);
                end = boundaries[1].isBlank()
                        ? totalSize - 1
                        : Long.parseLong(boundaries[1]);
            }

            if (start < 0 || start >= totalSize || end < start)
                throw new AttachmentRangeNotSatisfiableException(totalSize);

            end = Math.min(end, totalSize - 1);
            return new ByteRange(start, end, end - start + 1, true);
        } catch (NumberFormatException exception) {
            throw new AttachmentRangeNotSatisfiableException(totalSize);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank())
            return "application/octet-stream";
        return contentType;
    }

    private record ByteRange(long start, long end, long length, boolean partial) {
    }
}
