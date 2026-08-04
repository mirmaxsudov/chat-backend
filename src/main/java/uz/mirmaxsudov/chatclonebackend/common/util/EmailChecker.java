package uz.mirmaxsudov.chatclonebackend.common.util;

public class EmailChecker {
    public static boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }
}
