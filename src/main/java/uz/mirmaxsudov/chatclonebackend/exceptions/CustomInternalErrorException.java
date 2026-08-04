package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomInternalErrorException extends RuntimeException{

    public CustomInternalErrorException(String message) {
        super(message);
    }
}
