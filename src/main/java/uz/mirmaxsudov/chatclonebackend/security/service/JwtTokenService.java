package uz.mirmaxsudov.chatclonebackend.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.model.response.auth.LoginResponse;
import uz.mirmaxsudov.chatclonebackend.security.config.JwtProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public LoginResponse createAccessToken(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(properties.accessExpirationMs());
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(principal.user().getId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();

        return new LoginResponse(accessToken, "Bearer", expiresAt);
    }
}
