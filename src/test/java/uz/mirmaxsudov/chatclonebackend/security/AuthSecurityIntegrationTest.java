package uz.mirmaxsudov.chatclonebackend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.Role;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;
import uz.mirmaxsudov.chatclonebackend.repository.auth.RoleRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthSecurityIntegrationTest {
    private static final String PHONE_NUMBER = "+998901234567";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void createUser() {
        Role userRole = roleRepository.save(new Role(RoleName.USER));
        userRepository.save(User.builder()
                .phoneNumber(PHONE_NUMBER)
                .username("security-test-user")
                .firstname("Security")
                .lastname("Tester")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(Set.of(userRole))
                .build());
    }

    @Test
    void loginReturnsTokenThatAuthenticatesProfileRequest() throws Exception {
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "+998901234567",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(loginBody);
        String accessToken = responseJson.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phoneNumber").value(PHONE_NUMBER))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutRevealingWhichCredentialFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "+998901234567",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid phone number or password"));
    }

    @Test
    void protectedEndpointRejectsMissingTokenWithJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void protectedEndpointRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void sockJsHandshakeEndpointIsOpenForStompLevelAuthentication() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.websocket").value(true));
    }

    @Test
    void asyncApiDocumentationIsPublicAndContainsPrivateMessageTopic() throws Exception {
        mockMvc.perform(get("/springwolf/docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Chat Realtime API"))
                .andExpect(jsonPath("$.servers.chat-websocket.pathname").value("/ws"))
                .andExpect(jsonPath("$.channels._user_queue_messages.address")
                        .value("/user/queue/messages"))
                .andExpect(jsonPath("$.components.schemas.RealtimeMessageEvent").exists());

        mockMvc.perform(get("/springwolf/asyncapi-ui.html"))
                .andExpect(status().isOk());
    }

    @Test
    void loginValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.phoneNumber").value("Phone number is required"))
                .andExpect(jsonPath("$.password").value("Password is required"));
    }
}
