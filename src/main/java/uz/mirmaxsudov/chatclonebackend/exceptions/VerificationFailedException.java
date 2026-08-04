package uz.mirmaxsudov.chatclonebackend.exceptions;

public class VerificationFailedException extends RuntimeException {
    public VerificationFailedException(String message) {
        super(message);
    }
}