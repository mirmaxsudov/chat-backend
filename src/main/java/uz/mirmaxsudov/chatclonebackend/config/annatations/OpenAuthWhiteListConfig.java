package uz.mirmaxsudov.chatclonebackend.config.annatations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uz.mirmaxsudov.chatclonebackend.annotations.OpenAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenAuthWhiteListConfig {
    @Bean
    public List<RequestMatcher> openAuthWhiteList(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) {
        List<RequestMatcher> matchers = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();

            if (!isOpenEndpoint(handler)) {
                continue;
            }

            addMatchers(matchers, mapping);
        }

        return List.copyOf(matchers);
    }

    private boolean isOpenEndpoint(HandlerMethod handler) {
        return handler.hasMethodAnnotation(OpenAuth.class)
                || handler.getBeanType().isAnnotationPresent(OpenAuth.class);
    }

    private void addMatchers(List<RequestMatcher> matchers, RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();

        for (String pattern : mapping.getPatternValues()) {
            if (methods.isEmpty()) {
                matchers.add(PathPatternRequestMatcher.pathPattern(pattern));
                continue;
            }

            for (RequestMethod method : methods) {
                matchers.add(PathPatternRequestMatcher.pathPattern(
                        HttpMethod.valueOf(method.name()),
                        pattern
                ));

                if (method == RequestMethod.GET) {
                    matchers.add(PathPatternRequestMatcher.pathPattern(HttpMethod.HEAD, pattern));
                }
            }
        }
    }
}
