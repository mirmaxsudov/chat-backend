package uz.mirmaxsudov.chatclonebackend.service.impl.user;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.config.cache.CacheNames;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.Role;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;
import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.user.UserService;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            sync = true,
            key = "#userId",
            cacheNames = CacheNames.USER_PROFILE_RESPONSE
    )
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomNotFoundException("User not found"));

        Set<RoleName> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        return new UserProfileResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getUsername(),
                user.getFirstname(),
                user.getLastname(),
                roles
        );
    }
}
