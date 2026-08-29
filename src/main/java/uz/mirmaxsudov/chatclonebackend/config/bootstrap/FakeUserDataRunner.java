package uz.mirmaxsudov.chatclonebackend.config.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.Role;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;
import uz.mirmaxsudov.chatclonebackend.repository.auth.RoleRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.seed.fake-users",
        name = "enabled",
        havingValue = "true"
)
public class FakeUserDataRunner implements ApplicationRunner {
    private static final List<FakeUser> FAKE_USERS = List.of(
            new FakeUser("+998901000001", "ali.valiyev", "Ali", "Valiyev"),
            new FakeUser("+998901000002", "laylo.karimova", "Laylo", "Karimova"),
            new FakeUser("+998901000003", "bekzod.rasulov", "Bekzod", "Rasulov"),
            new FakeUser("+998901000004", "malika.tursunova", "Malika", "Tursunova"),
            new FakeUser("+998901000005", "sardor.ergashev", "Sardor", "Ergashev")
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.fake-users.password}")
    private String fakeUserPassword;

    @Value("${app.seed.fake-users.enabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled)
            return;

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.USER)));
        String passwordHash = passwordEncoder.encode(fakeUserPassword);

        List<User> usersToInsert = FAKE_USERS.stream()
                .filter(fakeUser -> !userRepository.existsByPhoneNumberOrUsername(
                        fakeUser.phoneNumber(),
                        fakeUser.username()
                ))
                .map(fakeUser -> toUser(fakeUser, passwordHash, userRole))
                .toList();

        userRepository.saveAll(usersToInsert);
        log.info("Inserted {} fake development users", usersToInsert.size());
    }

    private User toUser(FakeUser fakeUser, String passwordHash, Role userRole) {
        return User.builder()
                .phoneNumber(fakeUser.phoneNumber())
                .username(fakeUser.username())
                .firstname(fakeUser.firstname())
                .lastname(fakeUser.lastname())
                .passwordHash(passwordHash)
                .roles(Set.of(userRole))
                .build();
    }

    private record FakeUser(
            String phoneNumber,
            String username,
            String firstname,
            String lastname
    ) {
    }
}
