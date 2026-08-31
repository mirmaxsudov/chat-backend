package uz.mirmaxsudov.chatclonebackend.service.base.user;

import org.springframework.data.domain.Page;
import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.PublicUserResponse;

import java.util.UUID;

public interface UserService {
    UserProfileResponse getProfile(UUID userId);

    Page<PublicUserResponse> searchByUsername(UUID currentUserId, String username, int page, int size);
}
