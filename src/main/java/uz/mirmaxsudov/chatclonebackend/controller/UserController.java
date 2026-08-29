package uz.mirmaxsudov.chatclonebackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;
import uz.mirmaxsudov.chatclonebackend.service.base.user.UserService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIUtil.API_BASE_URL + "users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserProfileResponse profile = userService.getProfile(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .success(true)
                .message("User profile retrieved")
                .data(profile)
                .build());
    }
}
