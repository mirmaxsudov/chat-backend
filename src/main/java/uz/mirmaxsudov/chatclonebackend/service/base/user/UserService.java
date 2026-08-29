package uz.mirmaxsudov.chatclonebackend.service.base.user;

import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;

import java.util.UUID;

public interface UserService {
    UserProfileResponse getProfile(UUID userId);
}
