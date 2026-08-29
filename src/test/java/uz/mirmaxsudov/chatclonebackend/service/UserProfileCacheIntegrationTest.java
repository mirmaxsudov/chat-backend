package uz.mirmaxsudov.chatclonebackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.config.cache.CacheDefinition;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.Role;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;
import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;
import uz.mirmaxsudov.chatclonebackend.repository.auth.RoleRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.user.UserService;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserProfileCacheIntegrationTest {

    @Autowired
    private UserService userService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        clearUserProfileCache();

        Role userRole = roleRepository.save(new Role(RoleName.USER));
        User user = userRepository.save(User.builder()
                .phoneNumber("+998901112233")
                .username("cached-user")
                .firstname("Cached")
                .lastname("User")
                .passwordHash(passwordEncoder.encode("StrongPassword123!"))
                .roles(Set.of(userRole))
                .build());

        userId = user.getId();
        clearInvocations(userRepository);
    }

    @AfterEach
    void tearDown() {
        clearUserProfileCache();
    }

    @Test
    void repeatedProfileReadsUseCache() {
        UserProfileResponse firstResponse = userService.getProfile(userId);
        UserProfileResponse secondResponse = userService.getProfile(userId);

        assertThat(secondResponse).isEqualTo(firstResponse);
        verify(userRepository, times(1)).findByIdAndDeletedFalse(userId);
    }

    private void clearUserProfileCache() {
        var cache = cacheManager.getCache(CacheDefinition.USER_PROFILE_RESPONSE.getCacheName());
        assertThat(cache).isNotNull();
        cache.clear();
    }
}
