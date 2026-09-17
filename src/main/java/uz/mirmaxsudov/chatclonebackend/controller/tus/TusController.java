package uz.mirmaxsudov.chatclonebackend.controller.tus;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.model.tus.DownloadPayload;
import uz.mirmaxsudov.chatclonebackend.model.tus.TusUpload;
import uz.mirmaxsudov.chatclonebackend.service.tus.UploadService;
import uz.mirmaxsudov.chatclonebackend.tus.TusMetadataParser;
import uz.mirmaxsudov.chatclonebackend.tus.TusProtocolException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TusController {
    private static final String TUS_VERSION = "1.0.0";
    private static final String HEADER_TUS_RESUMABLE = "Tus-Resumable";
    private static final String HEADER_UPLOAD_LENGTH = "Upload-Length";
    private static final String HEADER_UPLOAD_OFFSET = "Upload-Offset";
    private static final String HEADER_UPLOAD_METADATA = "Upload-Metadata";
    private static final String HEADER_TUS_EXTENSION = "Tus-Extension";
    private static final String HEADER_TUS_VERSION = "Tus-Version";
    private static final String HEADER_TUS_MAX_SIZE = "Tus-Max-Size";
    private static final String HEADER_ATTACHMENT_ID = "Upload-Attachment-Id";

    private final UploadService uploadService;
    private final TusProperties tusProperties;

    @RequestMapping(value = {"", "/{id}"}, method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        HttpHeaders headers = baseTusHeaders();
        headers.add(HEADER_TUS_EXTENSION, "creation,termination");
        headers.add(HEADER_TUS_MAX_SIZE, String.valueOf(tusProperties.getMaxUploadSizeBytes()));
        return ResponseEntity.noContent().headers(headers).build();
    }

    @PostMapping
    public ResponseEntity<Void> createUpload(
            @RequestHeader(value = HEADER_TUS_RESUMABLE, required = false) String tusResumable,
            @RequestHeader(value = HEADER_UPLOAD_LENGTH, required = false) Long uploadLength,
            @RequestHeader(value = HEADER_UPLOAD_METADATA, required = false) String uploadMetadata,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        validateTusResumable(tusResumable);

        if (uploadLength == null)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload-Length header is required");

        Map<String, String> metadata = TusMetadataParser.parse(uploadMetadata);
        TusUpload upload = uploadService.createUpload(uploadLength, metadata, userId(jwt));

        String location = request.getRequestURL().toString();
        if (!location.endsWith("/"))
            location += "/";

        location += upload.getId();

        HttpHeaders headers = baseTusHeaders();
        headers.add(HttpHeaders.LOCATION, location);
        headers.add(HEADER_UPLOAD_OFFSET, String.valueOf(upload.getOffset()));
        headers.add(HEADER_UPLOAD_LENGTH, String.valueOf(upload.getUploadLength()));

        return ResponseEntity.created(URI.create(location)).headers(headers).build();
    }

    @PatchMapping(value = "/{id}", consumes = "application/offset+octet-stream")
    public ResponseEntity<Void> patchUpload(
            @PathVariable String id,
            @RequestHeader(value = HEADER_TUS_RESUMABLE, required = false) String tusResumable,
            @RequestHeader(value = HEADER_UPLOAD_OFFSET, required = false) Long uploadOffset,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) throws Exception {
        validateTusResumable(tusResumable);

        if (uploadOffset == null)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Upload-Offset header is required");

        long chunkSize = request.getContentLengthLong();

        if (chunkSize <= 0)
            throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Content-Length must be greater than zero for PATCH");

        long newOffset = uploadService.appendChunk(
                id,
                userId(jwt),
                uploadOffset,
                chunkSize,
                request.getInputStream()
        );

        HttpHeaders headers = baseTusHeaders();
        headers.add(HEADER_UPLOAD_OFFSET, String.valueOf(newOffset));
        addAttachmentId(headers, uploadService.getUpload(id, userId(jwt)));

        return ResponseEntity.noContent().headers(headers).build();
    }

    @RequestMapping(path = "/{id}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headUpload(
            @PathVariable String id,
            @RequestHeader(value = HEADER_TUS_RESUMABLE, required = false) String tusResumable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        validateTusResumable(tusResumable);

        TusUpload upload = uploadService.getUpload(id, userId(jwt));
        HttpHeaders headers = baseTusHeaders();
        headers.add(HEADER_UPLOAD_OFFSET, String.valueOf(upload.getOffset()));
        headers.add(HEADER_UPLOAD_LENGTH, String.valueOf(upload.getUploadLength()));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        addAttachmentId(headers, upload);

        return ResponseEntity.ok().headers(headers).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUpload(
            @PathVariable String id,
            @RequestHeader(value = HEADER_TUS_RESUMABLE, required = false) String tusResumable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        validateTusResumable(tusResumable);
        uploadService.deleteUpload(id, userId(jwt));
        return ResponseEntity.noContent().headers(baseTusHeaders()).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range
    ) {
        DownloadPayload payload = uploadService.openDownload(id, userId(jwt), range);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (payload.partial())
            headers.add(HttpHeaders.CONTENT_RANGE,
                    "bytes " + payload.start() + "-" + payload.end() + "/" + payload.totalSize());

        if (payload.fileName() != null && !payload.fileName().isBlank())
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(payload.fileName(), StandardCharsets.UTF_8)
                    .build());

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(payload.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.status(payload.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers)
                .contentLength(payload.contentLength())
                .contentType(mediaType)
                .body(new InputStreamResource(payload.stream()));
    }

    private void validateTusResumable(String headerValue) {
        if (headerValue == null || headerValue.isBlank())
            throw new TusProtocolException(HttpStatus.PRECONDITION_FAILED, "Tus-Resumable header is required");

        if (!TUS_VERSION.equals(headerValue))
            throw new TusProtocolException(HttpStatus.PRECONDITION_FAILED,
                    "Unsupported Tus-Resumable version: " + headerValue);
    }

    private HttpHeaders baseTusHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_TUS_RESUMABLE, TUS_VERSION);
        headers.add(HEADER_TUS_VERSION, TUS_VERSION);
        return headers;
    }

    private void addAttachmentId(HttpHeaders headers, TusUpload upload) {
        if (upload.getAttachmentId() != null)
            headers.add(HEADER_ATTACHMENT_ID, upload.getAttachmentId().toString());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
