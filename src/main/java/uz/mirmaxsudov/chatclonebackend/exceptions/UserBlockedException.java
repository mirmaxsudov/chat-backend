package uz.mirmaxsudov.chatclonebackend.exceptions;

public class UserBlockedException extends RuntimeException {
    public UserBlockedException(String message) {
        super(message);
    }
}
