package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomInvalidDtoException extends RuntimeException {
    public CustomInvalidDtoException(String message) {
        super(message);
    }
}