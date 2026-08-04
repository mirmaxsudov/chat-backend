package uz.mirmaxsudov.chatclonebackend.model.response;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPaginateResponse<T> {
    private boolean success;
    private String message;
    private T results;
    private int total;
    private int page;
    private int size;
    private boolean hasNext;
}
