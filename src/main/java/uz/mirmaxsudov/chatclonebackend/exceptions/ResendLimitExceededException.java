package uz.mirmaxsudov.chatclonebackend.exceptions;

public class ResendLimitExceededException extends RuntimeException {
    public ResendLimitExceededException(String message) {
        super(message);
    }
}
