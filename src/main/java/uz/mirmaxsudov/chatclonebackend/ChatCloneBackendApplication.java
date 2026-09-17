package uz.mirmaxsudov.chatclonebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatCloneBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatCloneBackendApplication.class, args);
    }
}
