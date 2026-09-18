package uz.mirmaxsudov.chatclonebackend.presence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PresenceRepositoryIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private ChatService chatService;

    @Test
    void directPeerLookupAndLastSeenUpdateUsePersistedRelationships() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User first = saveUser("+99893" + suffix, "presence.first." + suffix);
        User second = saveUser("+99894" + suffix, "presence.second." + suffix);
        User outsider = saveUser("+99895" + suffix, "presence.outsider." + suffix);
        chatService.createOrGetDm(first.getId(), second.getUsername());

        assertThat(chatMemberRepository.findActiveDirectPeerIds(first.getId()))
                .containsExactly(second.getId())
                .doesNotContain(outsider.getId());

        Instant lastSeenAt = Instant.parse("2026-09-18T10:15:30Z");
        assertThat(userRepository.updateLastSeenAt(first.getId(), lastSeenAt)).isEqualTo(1);
        assertThat(userRepository.findByIdAndDeletedFalse(first.getId()))
                .get()
                .extracting(User::getLastSeenAt)
                .isEqualTo(lastSeenAt);
    }

    private User saveUser(String phoneNumber, String username) {
        return userRepository.save(User.builder()
                .phoneNumber(phoneNumber)
                .username(username)
                .firstname("Presence")
                .lastname("Tester")
                .passwordHash("not-used-in-this-test")
                .build());
    }
}
