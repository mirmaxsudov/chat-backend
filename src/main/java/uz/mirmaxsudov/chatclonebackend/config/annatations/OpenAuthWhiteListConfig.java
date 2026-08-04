//package uz.mirmaxsudov.chatclonebackend.config.annatations;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.HttpMethod;
//import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
//import org.springframework.security.web.util.matcher.RequestMatcher;
//import org.springframework.web.bind.annotation.RequestMethod;
//import org.springframework.web.method.HandlerMethod;
//import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
//import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
//import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
//import org.springframework.web.util.pattern.PathPattern;
//import uz.mirmaxsudov.lmsbackend.annotations.OpenAuth;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//@Configuration
//@RequiredArgsConstructor
//public class OpenAuthWhiteListConfig {
//    @Bean
//    public List<RequestMatcher> openAuthWhiteList(
//            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
//            HandlerMappingIntrospector introspector
//    ) {
//        List<RequestMatcher> matchers = new ArrayList<>();
//
//        MvcRequestMatcher.Builder builder =
//                new MvcRequestMatcher.Builder(introspector);
//
//        Map<RequestMappingInfo, HandlerMethod> mp =
//                handlerMapping.getHandlerMethods();
//
//        for (var entry : mp.entrySet()) {
//            RequestMappingInfo info = entry.getKey();
//            HandlerMethod handler = entry.getValue();
//
//            boolean isOpen =
//                    handler.hasMethodAnnotation(OpenAuth.class) ||
//                            handler.getBeanType().isAnnotationPresent(OpenAuth.class);
//
//            if (!isOpen) continue;
//
//            Set<RequestMethod> methods =
//                    info.getMethodsCondition().getMethods();
//
//            Set<PathPattern> patterns =
//                    info.getPathPatternsCondition().getPatterns();
//
//            for (PathPattern pp : patterns) {
//                String pattern = pp.getPatternString();
//
//                if (methods.isEmpty()) {
//                    matchers.add(builder.pattern(pattern));
//                } else {
//                    for (RequestMethod method : methods) {
//                        matchers.add(
//                                builder.pattern(
//                                        HttpMethod.valueOf(method.name()),
//                                        pattern
//                                )
//                        );
//                    }
//                }
//            }
//        }
//
//        return List.copyOf(matchers);
//    }
//}