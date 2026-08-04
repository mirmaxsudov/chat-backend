package uz.mirmaxsudov.chatclonebackend.config.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SwaggerConfig {
    private static final String SECURITY_SCHEME_NAME = "Bearer";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LMS")
                        .version("1.0.0")
                        .description("LMS")
                        .contact(new Contact()
                                .name("Abdurahmon Mirmaxsudov")
                                .email("abdurahmonmirmaxsudov2804@gmail.com")
                                .url("https://t.me/AbdurahmonMirmaxsudov")
                                .extensions(
                                        Map.of(
                                                "Telegram", "https://t.me/MirmaxsudovAbdurahmon",
                                                "GitHub", "https://github.com/mirmaxsudov"
                                        )
                                )
                        )
                ).addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}