package uz.mirmaxsudov.chatclonebackend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.mirmaxsudov.chatclonebackend.annotations.OpenAuth;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.model.request.auth.LoginRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.auth.LoginResponse;
import uz.mirmaxsudov.chatclonebackend.service.base.auth.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIUtil.API_BASE_URL + "auth")
public class AuthController {
    private final AuthService authService;

    @OpenAuth
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
