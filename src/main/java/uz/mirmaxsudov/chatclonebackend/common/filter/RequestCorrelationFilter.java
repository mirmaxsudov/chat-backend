package uz.mirmaxsudov.chatclonebackend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(REQUEST_ID_MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches())
            return candidate;

        return UUID.randomUUID().toString();
    }
}
