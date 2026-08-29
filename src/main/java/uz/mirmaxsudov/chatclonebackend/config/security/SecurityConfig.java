package uz.mirmaxsudov.chatclonebackend.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import uz.mirmaxsudov.chatclonebackend.security.handler.RestAccessDeniedHandler;
import uz.mirmaxsudov.chatclonebackend.security.handler.RestAuthenticationEntryPoint;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("openAuthWhiteList") List<RequestMatcher> openAuthWhiteList
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> {
                    if (!openAuthWhiteList.isEmpty()) {
                        authorize.requestMatchers(openAuthWhiteList.toArray(RequestMatcher[]::new))
                                .permitAll();
                    }

                    authorize
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).permitAll()
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
