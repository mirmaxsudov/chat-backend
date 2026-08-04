package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomIllegalArgumentException extends RuntimeException {
    public CustomIllegalArgumentException(String message) {
        super(message);
    }
}
