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

    public SocialUser verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);
            return new SocialUser(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    verifiedEmail(jwt),
                    jwt.getClaimAsString("name")
            );
        } catch (JwtException e) {
            throw new UnauthorizedException("유효하지 않은 구글 로그인입니다.");
        }
    }

    /**
     * 구글이 "이 주소가 그 사람 것임을 우리가 확인했다"고 말하는가.
     *
     * 전에는 이 클레임을 보지 않고 email 만 꺼내 썼다. 구글은 거의 언제나
     * 확인된 주소만 주기 때문에 실제로 문제가 된 적은 없었을 것이다.
     * 그래도 읽는 이유는, 이 값으로 기존 계정을 이어붙일지 정하기 때문이다.
     * 「거의 언제나」를 근거로 계정을 넘겨주고 있었던 셈이다.
     *
     * 구글은 이 클레임을 boolean 으로 주지만, 문자열 "true" 로 주는 제공자도 있어
     * 두 경우를 다 받는다.
     */
    private static boolean verifiedEmail(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        if (claim instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(claim));
    }
}