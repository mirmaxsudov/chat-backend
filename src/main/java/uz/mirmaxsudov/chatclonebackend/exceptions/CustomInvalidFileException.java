package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomInvalidFileException extends RuntimeException {
    public CustomInvalidFileException(String message) {
        super(message);
    }
}