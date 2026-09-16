package uz.mirmaxsudov.chatclonebackend.model.tus;

import java.io.InputStream;

public record DownloadPayload(
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
