package uz.mirmaxsudov.chatclonebackend.security.handler;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiErrorResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(message, status, LocalDateTime.now(), status.value())
        );
    }
}
