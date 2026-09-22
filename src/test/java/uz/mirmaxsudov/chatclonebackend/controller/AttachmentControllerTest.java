package uz.mirmaxsudov.chatclonebackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.attachment.AttachmentClientResponse;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentService;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentControllerTest {
    private static final UUID ATTACHMENT_ID = UUID.randomUUID();

    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final AttachmentController controller = new AttachmentController(attachmentService);

    @Test
    void returnsPartialContentHeadersForRangeRequest() {
        AttachmentClientResponse attachment = new AttachmentClientResponse(
                new ByteArrayInputStream(new byte[0]),
                1_000,
                100,
                100,
                199,
                true,
                "video/mp4",
                "video.mp4"
        );

        when(attachmentService.getClientAttachment(ATTACHMENT_ID, "bytes=100-199", true))
                .thenReturn(attachment);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        ResponseEntity<InputStreamResource> response = controller.getClientAttachment(
                ATTACHMENT_ID,
                "bytes=100-199",
                request
        );

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals("bytes 100-199/1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals(100, response.getHeaders().getContentLength());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.hasBody());
    }

    @Test
    void headReturnsHeadersWithoutOpeningAResponseBody() {
        AttachmentClientResponse attachment = new AttachmentClientResponse(
                null,
                1_000,
                1_000,
                0,
                999,
                false,
                "image/jpeg",
                "photo.jpg"
        );
        when(attachmentService.getClientAttachment(ATTACHMENT_ID, null, false))
                .thenReturn(attachment);

        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/");
        ResponseEntity<InputStreamResource> response = controller.getClientAttachment(
                ATTACHMENT_ID,
                null,
                request
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1_000, response.getHeaders().getContentLength());
        assertNull(response.getBody());
        verify(attachmentService).getClientAttachment(ATTACHMENT_ID, null, false);
    }

    @Test
    void returnsVideoThumbnailAsInlineImage() {
        AttachmentClientResponse thumbnail = new AttachmentClientResponse(
                new ByteArrayInputStream(new byte[0]),
                100,
                100,
                0,
                99,
                false,
                "image/jpeg",
                "video.mp4.jpg"
        );
        when(attachmentService.getPreview(ATTACHMENT_ID, null, true))
                .thenReturn(thumbnail);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        ResponseEntity<InputStreamResource> response = controller.getPreview(
                ATTACHMENT_ID,
                null,
                request
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        assertTrue(response.hasBody());
        assertEquals("public, max-age=31536000, immutable", response.getHeaders().getCacheControl());
        verify(attachmentService).getPreview(ATTACHMENT_ID, null, true);
    }
}
