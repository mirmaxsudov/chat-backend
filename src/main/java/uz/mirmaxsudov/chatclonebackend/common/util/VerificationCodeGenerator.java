package uz.mirmaxsudov.chatclonebackend.common.util;

public class VerificationCodeGenerator {
    public static String generateVerificationCode() { // 6 digits
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }
}