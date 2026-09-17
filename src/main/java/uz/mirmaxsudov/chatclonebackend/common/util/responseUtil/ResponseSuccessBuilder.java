package uz.mirmaxsudov.chatclonebackend.common.util.responseUtil;

import uz.mirmaxsudov.chatclonebackend.model.response.ApiResponse;

public interface ResponseSuccessBuilder {
    static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

}
