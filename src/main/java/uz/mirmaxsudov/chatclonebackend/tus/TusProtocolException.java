package uz.mirmaxsudov.chatclonebackend.tus;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TusProtocolException extends RuntimeException {
    private final HttpStatus status;

    public TusProtocolException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}