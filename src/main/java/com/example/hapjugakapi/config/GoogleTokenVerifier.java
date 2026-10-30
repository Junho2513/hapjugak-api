package com.example.hapjugakapi.config;

import com.example.hapjugakapi.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();

        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>("iss", GoogleTokenVerifier::isGoogle),
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.contains(clientId))
        ));

        this.decoder = nimbus;
    }

    private static boolean isGoogle(String issuer) {
        return "https://accounts.google.com".equals(issuer)
                || "accounts.google.com".equals(issuer);
    }

    public GoogleUser verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);
            return new GoogleUser(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name")
            );
        } catch (JwtException e) {
            throw new UnauthorizedException("유효하지 않은 구글 로그인입니다.");
        }
    }

    public record GoogleUser(String sub, String email, String name) {
    }
}