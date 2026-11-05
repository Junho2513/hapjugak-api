package com.example.hapjugakapi.config;

import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 네이버 로그인.
 *
 * 코드 → 접근 토큰 → 사람 정보. 카카오와 같은 모양이고 주소와 칸 이름만 다르다.
 * 네이버는 OIDC(ID 토큰)를 지원하지 않아서 이 길밖에 없다.
 *
 * ── 이메일을 믿지 않는 이유 ─────────────────────────────────────────
 * 네이버는 이메일이 본인 확인을 거친 것인지 <b>알려주지 않는다.</b>
 * 구글의 email_verified, 카카오의 is_email_verified 에 해당하는 칸이 없다.
 *
 * 그래서 이 구현은 네이버 이메일을 늘 「확인되지 않음」으로 본다.
 * 알 수 없는 것을 true 로 두는 쪽이 비용이 훨씬 크다 — 그 값 하나로
 * 기존 계정을 넘겨줄지가 정해지기 때문이다.
 *
 * 대신 치르는 값이 있다. 구글로 가입한 사람이 같은 주소의 네이버로 들어오면
 * 자동으로 이어지지 않는다. AuthService 는 그 경우 계정을 새로 만들지 않고
 * "기존 방법으로 로그인해 달라"고 막는다. 말없이 계정이 둘로 갈라지는 것보다
 * 낫다고 봤다.
 *
 * 네이버가 나중에 인증 여부를 알려주게 되면 이 주석과 함께 고치면 된다.
 */
@Component
public class NaverOAuthClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(NaverOAuthClient.class);
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_URI = "https://openapi.naver.com/v1/nid/me";

    private final RestClient rest = SocialHttp.client();
    private final String clientId;
    private final String clientSecret;

    public NaverOAuthClient(@Value("${naver.client-id}") String clientId,
                            @Value("${naver.client-secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // 네이버는 토큰 교환에 redirect_uri 를 요구하지 않는다. code 와 state 만 본다.

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    public SocialUser exchange(SocialCode code) {
        NaverUser user = fetchUser(accessToken(code));

        if (user == null || user.response() == null || user.response().id() == null) {
            log.warn("네이버가 사용자 번호를 주지 않았습니다: {}", user);
            throw new UnauthorizedException("네이버 로그인에 실패했습니다. 다시 시도해주세요.");
        }

        Profile p = user.response();
        // 세 번째 인자가 늘 false 인 것은 일부러다. 위 주석 참고.
        return new NaverSocialUser(p.id(), p.email(), p.nickname()).toSocialUser();
    }

    /**
     * 네이버 토큰 교환은 form 이 아니라 질의 문자열로 받는다.
     * state 는 네이버만 요구한다 — 띄울 때 보낸 값과 같아야 한다.
     */
    private String accessToken(SocialCode code) {
        String uri = UriComponentsBuilder.fromUriString(TOKEN_URI)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code.code())
                .queryParam("state", code.state() == null ? "" : code.state())
                .build()
                .toUriString();

        TokenResponse res;
        try {
            res = rest.get().uri(uri).retrieve().body(TokenResponse.class);
        } catch (RestClientException e) {
            // 코드가 이미 쓰였거나, 만료됐거나, 키·state 가 틀린 경우다.
            log.warn("네이버 토큰 교환 실패", e);
            throw new UnauthorizedException("네이버 로그인에 실패했습니다. 다시 시도해주세요.");
        }

        if (res == null || res.accessToken() == null) {
            // 네이버는 실패해도 200 에 error 칸을 담아 보낼 때가 있다.
            log.warn("네이버가 access_token 을 주지 않았습니다: {}", res);
            throw new UnauthorizedException("네이버 로그인에 실패했습니다. 다시 시도해주세요.");
        }
        return res.accessToken();
    }

    private NaverUser fetchUser(String accessToken) {
        try {
            return rest.get()
                    .uri(USER_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(NaverUser.class);
        } catch (RestClientException e) {
            log.warn("네이버 사용자 조회 실패", e);
            throw new UnauthorizedException("네이버 로그인에 실패했습니다. 다시 시도해주세요.");
        }
    }

    /** 네이버 이메일은 인증 여부를 알 수 없으므로 늘 확인되지 않은 것으로 본다. */
    private record NaverSocialUser(String id, String email, String nickname) {
        SocialUser toSocialUser() {
            return new SocialUser(id, email, false, nickname);
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record NaverUser(String resultcode, String message, Profile response) {
    }

    private record Profile(String id, String email, String nickname) {
    }
}
