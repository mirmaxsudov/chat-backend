package uz.mirmaxsudov.chatclonebackend.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import uz.mirmaxsudov.chatclonebackend.listener.chat.RealtimeMessagePublisher;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.ChatResponse;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class RealtimeMessageAfterCommitIntegrationTest {
    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void restMessageIsPublishedToBothDmUsersOnlyAfterCommit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User sender = saveUser("+99891" + suffix, "sender." + suffix);
        User recipient = saveUser("+99892" + suffix, "recipient." + suffix);
        ChatResponse chat = chatService.createOrGetDm(sender.getId(), recipient.getUsername());
        clearInvocations(messagingTemplate);

        transactionTemplate.executeWithoutResult(status -> {
            chatService.sendMessage(sender.getId(), chat.id(), "Committed message");
            verify(messagingTemplate, never()).convertAndSendToUser(
                    any(String.class),
                    eq(RealtimeMessagePublisher.USER_MESSAGE_QUEUE),
                    any()
            );
        });

        verify(messagingTemplate, times(2)).convertAndSendToUser(
                any(String.class),
                eq(RealtimeMessagePublisher.USER_MESSAGE_QUEUE),
                any()
        );
    }

    private User saveUser(String phoneNumber, String username) {
        return userRepository.save(User.builder()
                .phoneNumber(phoneNumber)
                .username(username)
                .firstname("Realtime")
                .lastname("Tester")
                .passwordHash("not-used-in-this-test")
                .build());
    }
}
