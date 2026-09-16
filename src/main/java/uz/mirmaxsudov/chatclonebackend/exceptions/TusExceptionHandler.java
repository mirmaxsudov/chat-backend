package uz.mirmaxsudov.chatclonebackend.exceptions;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.mirmaxsudov.chatclonebackend.controller.tus.TusController;
import uz.mirmaxsudov.chatclonebackend.storage.StorageException;
import uz.mirmaxsudov.chatclonebackend.tus.TusProtocolException;

import java.time.Instant;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TusController.class)
public class TusExceptionHandler {
    private static final String TUS_VERSION = "1.0.0";

    @ExceptionHandler(TusProtocolException.class)
    public ResponseEntity<Map<String, Object>> handleTusException(TusProtocolException ex) {
        return ResponseEntity.status(ex.getStatus()).headers(tusHeaders()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", ex.getStatus().value(),
                "error", ex.getStatus().getReasonPhrase(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(StorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(tusHeaders()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "message", ex.getMessage()
        ));
    }

    private HttpHeaders tusHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Tus-Resumable", TUS_VERSION);
        return headers;
    }
}
