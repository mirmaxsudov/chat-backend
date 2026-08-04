package uz.mirmaxsudov.chatclonebackend.exceptions;

public class CustomDuplicateStudentException extends RuntimeException {

    public CustomDuplicateStudentException(String message) {
        super(message);
    }
}