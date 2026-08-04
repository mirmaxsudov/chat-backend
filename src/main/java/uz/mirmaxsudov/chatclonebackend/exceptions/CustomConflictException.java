package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomConflictException extends RuntimeException{
    public CustomConflictException(String message) {
        super(message);
    }
}
