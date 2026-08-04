package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomAlreadyExistException extends RuntimeException {
    public CustomAlreadyExistException(String message) {
        super(message);
    }
}