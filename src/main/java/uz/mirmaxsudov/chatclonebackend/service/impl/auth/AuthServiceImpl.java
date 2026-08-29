package uz.mirmaxsudov.chatclonebackend.service.impl.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.exceptions.UnauthorizedException;
import uz.mirmaxsudov.chatclonebackend.model.request.auth.LoginRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.auth.LoginResponse;
import uz.mirmaxsudov.chatclonebackend.security.service.JwtTokenService;
import uz.mirmaxsudov.chatclonebackend.service.base.auth.AuthService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.phoneNumber().trim(),
                            request.password()
                    )
            );

            LoginResponse loginResponse = jwtTokenService.createAccessToken(authentication);

            return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                    .success(true)
                    .message("Login successful")
                    .data(loginResponse)
                    .build());
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException("Invalid phone number or password");
        }
    }
}
