package uz.mirmaxsudov.chatclonebackend.common.filter;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class PageableBuilder {
    public static Pageable build(int page, int size) {
        return PageRequest.of(page, size);
    }
}