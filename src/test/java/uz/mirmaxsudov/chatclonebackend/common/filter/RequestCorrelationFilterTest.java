package uz.mirmaxsudov.chatclonebackend.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesValidRequestIdAndMakesItAvailableDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "client-request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdDuringChain = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                requestIdDuringChain.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isEqualTo("client-request-42");
        assertThat(requestIdDuringChain).hasValue("client-request-42");
        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        String generatedRequestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertThat(generatedRequestId).isNotBlank();
        assertThat(UUID.fromString(generatedRequestId)).isNotNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe\r\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("unsafe\r\nvalue");
    }
}
