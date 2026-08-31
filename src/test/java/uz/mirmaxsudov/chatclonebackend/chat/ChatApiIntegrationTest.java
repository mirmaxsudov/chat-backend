package uz.mirmaxsudov.chatclonebackend.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.DmLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.SavedChatLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DmLinkRepository dmLinkRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private SavedChatLinkRepository savedChatLinkRepository;

    private User currentUser;
    private User targetUser;
    private User outsider;

    @BeforeEach
    void setUp() {
        currentUser = saveUser("+998901000101", "current.target", "Current", "User");
        targetUser = saveUser("+998901000102", "Target.User", "Direct", "Peer");
        outsider = saveUser("+998901000103", "unrelated.user", "Target", "ByNameOnly");
    }

    @Test
    void searchMatchesUsernameOnlyCaseInsensitivelyAndExcludesCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                        .with(jwtFor(currentUser))
                        .param("username", "TARGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].username").value("Target.User"))
                .andExpect(jsonPath("$.results[0].phoneNumber").doesNotExist());
    }

    @Test
    void creatingDmTwiceReturnsSameChatAndDoesNotDuplicateMembership() throws Exception {
        UUID firstChatId = createDm(currentUser, targetUser.getUsername());
        UUID secondChatId = createDm(currentUser, "@target.user");

        assertThat(secondChatId).isEqualTo(firstChatId);
        assertThat(dmLinkRepository.count()).isEqualTo(1);
        assertThat(chatMemberRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/chats")
                        .with(jwtFor(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.results[0].id").value(firstChatId.toString()))
                .andExpect(jsonPath("$.results[0].peer.username").value("Target.User"))
                .andExpect(jsonPath("$.results[0].lastMessage").isEmpty());
    }

    @Test
    void messageHistoryUsesDescendingSequenceCursorAndChatShowsLatestMessage() throws Exception {
        UUID chatId = createDm(currentUser, targetUser.getUsername());
        sendMessage(currentUser, chatId, "First message", 1);
        sendMessage(targetUser, chatId, "Second message", 2);
        sendMessage(currentUser, chatId, "Third message", 3);

        mockMvc.perform(get("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(currentUser))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].seq").value(3))
                .andExpect(jsonPath("$.data.messages[0].mine").value(true))
                .andExpect(jsonPath("$.data.messages[1].seq").value(2))
                .andExpect(jsonPath("$.data.messages[1].mine").value(false))
                .andExpect(jsonPath("$.data.nextBeforeSeq").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true));

        mockMvc.perform(get("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(currentUser))
                        .param("beforeSeq", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].seq").value(1))
                .andExpect(jsonPath("$.data.nextBeforeSeq").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));

        mockMvc.perform(get("/api/v1/chats/{chatId}", chatId)
                        .with(jwtFor(targetUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peer.username").value(currentUser.getUsername()))
                .andExpect(jsonPath("$.data.lastMessage.seq").value(3))
                .andExpect(jsonPath("$.data.lastMessage.text").value("Third message"))
                .andExpect(jsonPath("$.data.lastMessage.mine").value(false));
    }

    @Test
    void outsiderCannotReadOrSendMessages() throws Exception {
        UUID chatId = createDm(currentUser, targetUser.getUsername());

        mockMvc.perform(get("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Chat not found"));

        mockMvc.perform(post("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Unauthorized message"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Chat not found"));
    }

    @Test
    void dmAndMessageValidationRejectInvalidRequests() throws Exception {
        mockMvc.perform(post("/api/v1/chats/dm")
                        .with(jwtFor(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"current.target"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A direct chat cannot be created with yourself"));

        mockMvc.perform(post("/api/v1/chats/dm")
                        .with(jwtFor(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing.user"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        UUID chatId = createDm(currentUser, targetUser.getUsername());
        mockMvc.perform(post("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.text").value("Message text is required"));
    }

    @Test
    void chatEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void savedChatIsIdempotentOwnerOnlyAndSupportsRestMessages() throws Exception {
        UUID firstSavedChatId = createSavedChat(currentUser);
        UUID secondSavedChatId = createSavedChat(currentUser);

        assertThat(secondSavedChatId).isEqualTo(firstSavedChatId);
        assertThat(savedChatLinkRepository.count()).isEqualTo(1);
        assertThat(chatMemberRepository.count()).isEqualTo(1);

        sendMessage(currentUser, firstSavedChatId, "Remember this", 1);

        mockMvc.perform(get("/api/v1/chats")
                        .with(jwtFor(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.results[0].type").value("SAVED"))
                .andExpect(jsonPath("$.results[0].peer.username").value(currentUser.getUsername()))
                .andExpect(jsonPath("$.results[0].lastMessage.text").value("Remember this"));

        mockMvc.perform(get("/api/v1/chats/{chatId}/messages", firstSavedChatId)
                        .with(jwtFor(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Chat not found"));
    }

    private UUID createDm(User initiator, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chats/dm")
                        .with(jwtFor(initiator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DIRECT"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    private UUID createSavedChat(User owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chats/saved")
                        .with(jwtFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SAVED"))
                .andExpect(jsonPath("$.data.peer.id").value(owner.getId().toString()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    private void sendMessage(User sender, UUID chatId, String text, long expectedSequence) throws Exception {
        mockMvc.perform(post("/api/v1/chats/{chatId}/messages", chatId)
                        .with(jwtFor(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"%s"}
                                """.formatted(text)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seq").value(expectedSequence))
                .andExpect(jsonPath("$.data.text").value(text));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(User user) {
        return jwt().jwt(builder -> builder.subject(user.getId().toString()));
    }

    private User saveUser(String phoneNumber, String username, String firstname, String lastname) {
        return userRepository.save(User.builder()
                .phoneNumber(phoneNumber)
                .username(username)
                .firstname(firstname)
                .lastname(lastname)
                .passwordHash("not-used-in-this-test")
                .build());
    }
}
