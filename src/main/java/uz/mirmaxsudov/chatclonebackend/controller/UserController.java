package uz.mirmaxsudov.chatclonebackend.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiPaginateResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.PublicUserResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.UserProfileResponse;
import uz.mirmaxsudov.chatclonebackend.service.base.user.UserService;

import java.util.UUID;
import java.util.List;

@RestController
@Validated
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

    @GetMapping("/search")
    public ResponseEntity<ApiPaginateResponse<List<PublicUserResponse>>> searchByUsername(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam
            @NotBlank(message = "Username is required")
            @Size(max = 64, message = "Username must not exceed 64 characters")
            String username,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 50, message = "Size must not exceed 50")
            int size
    ) {
        Page<PublicUserResponse> users = userService.searchByUsername(
                UUID.fromString(jwt.getSubject()),
                username,
                page,
                size
        );

        return ResponseEntity.ok(ApiPaginateResponse.<List<PublicUserResponse>>builder()
                .success(true)
                .message("Users retrieved")
                .results(users.getContent())
                .total((int) Math.min(users.getTotalElements(), Integer.MAX_VALUE))
                .page(users.getNumber())
                .size(users.getSize())
                .hasNext(users.hasNext())
                .build());
    }
}
