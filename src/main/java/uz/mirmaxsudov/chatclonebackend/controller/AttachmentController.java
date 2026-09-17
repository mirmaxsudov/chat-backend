package uz.mirmaxsudov.chatclonebackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.model.response.attachment.AttachmentClientResponse;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentController {
    private final AttachmentService attachmentService;

    @RequestMapping(
            path = {
                    APIUtil.API_BASE_URL + "attachment/{id}",
                    APIUtil.API_BASE_URL + "attachments/{id}/content"
            },
            method = {RequestMethod.GET, RequestMethod.HEAD}
    )
    public ResponseEntity<InputStreamResource> getClientAttachment(
            @PathVariable("id") UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            HttpServletRequest request
    ) {
        boolean includeBody = !HttpMethod.HEAD.matches(request.getMethod());
        AttachmentClientResponse attachment = attachmentService.getClientAttachment(id, range, includeBody);

        HttpHeaders headers = new HttpHeaders();

        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add("X-Content-Type-Options", "nosniff");

        if (attachment.partial())
            headers.add(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + attachment.start() + "-" + attachment.end() + "/" + attachment.totalSize()
            );

        headers.setContentDisposition(ContentDisposition.inline()
                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                .build());

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.status(attachment.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers)
                .contentLength(attachment.contentLength())
                .contentType(mediaType)
                .body(attachment.stream() == null
                        ? null
                        : new InputStreamResource(attachment.stream()));
    }
}
