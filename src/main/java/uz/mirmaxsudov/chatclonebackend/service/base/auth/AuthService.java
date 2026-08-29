package uz.mirmaxsudov.chatclonebackend.service.base.auth;

import org.springframework.http.ResponseEntity;
import uz.mirmaxsudov.chatclonebackend.model.request.auth.LoginRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.auth.LoginResponse;

public interface AuthService {

    ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest request);
}
