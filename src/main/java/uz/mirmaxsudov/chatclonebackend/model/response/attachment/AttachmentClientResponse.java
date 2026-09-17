package uz.mirmaxsudov.chatclonebackend.model.response.attachment;

import java.io.InputStream;

public record AttachmentClientResponse(
        InputStream stream,
        long totalSize,
        long contentLength,
        long start,
        long end,
        boolean partial,
        String contentType,
        String fileName
) {
}
