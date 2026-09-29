package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomForbiddenException extends RuntimeException {
    public CustomForbiddenException(String message) {
        super(message);
    }
}
