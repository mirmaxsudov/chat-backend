package uz.mirmaxsudov.chatclonebackend.exceptions;


public class CustomNotFoundException extends RuntimeException {
    public CustomNotFoundException(String message) {
        super(message);
    }
}
