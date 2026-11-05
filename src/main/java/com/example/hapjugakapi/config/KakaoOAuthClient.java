package com.example.hapjugakapi.config;

import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인.
 *
 * 코드 → 접근 토큰 → 사람 정보, 두 번 묻는다. 구글처럼 ID 토큰 안에서 꺼내는
 * 길도 있지만(카카오도 OIDC 를 지원한다) 그러려면 개발자 콘솔에서 OIDC 를 따로
 * 켜야 하고, 네이버는 그 길이 아예 없다. 두 곳을 같은 모양으로 두는 쪽을 골랐다.
 *
 * ── 이메일이 까다롭다 ───────────────────────────────────────────────
 * 카카오계정 이메일은 <b>선택 동의</b>라서 사용자가 거부하면 아예 오지 않는다.
 * 그리고 와도 믿을 수 있는 것이 아니다. 응답에 세 가지가 따로 온다.
 *
 *   email              주소
 *   is_email_valid     그 주소가 카카오에서 쓸 수 있는 상태인가
 *   is_email_verified  카카오가 본인 것임을 확인했는가
 *
 * 둘 다 true 일 때만 믿는다. 이 구분이 중요한 이유는 AuthService 가 이메일로
 * 기존 계정을 이어붙이기 때문이다. 미인증 주소를 믿으면, 공격자가 자기 카카오
 * 계정의 이메일을 남의 주소로 바꾸는 것만으로 그 사람 계정에 들어간다.
 */
@Component
public class KakaoOAuthClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient rest = SocialHttp.client();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthClient(@Value("${kakao.client-id}") String clientId,
                            @Value("${kakao.client-secret}") String clientSecret,
                            @Value("${kakao.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialUser exchange(SocialCode code) {
        String accessToken = accessToken(code);
        KakaoUser user = fetchUser(accessToken);

        if (user == null || user.id() == null) {
            log.warn("카카오가 사용자 번호를 주지 않았습니다: {}", user);
            throw new UnauthorizedException("카카오 로그인에 실패했습니다. 다시 시도해주세요.");
        }

        KakaoAccount account = user.kakaoAccount();
        String email = (account == null) ? null : account.email();
        boolean trusted = account != null
                && Boolean.TRUE.equals(account.isEmailValid())
                && Boolean.TRUE.equals(account.isEmailVerified());

        return new SocialUser(String.valueOf(user.id()), email, trusted, nicknameOf(user));
    }

    private String accessToken(SocialCode code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code.code());

        TokenResponse res;
        try {
            res = rest.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // 코드가 이미 쓰였거나, 만료됐거나, 키·redirect_uri 가 틀린 경우다.
            // 자세한 내용은 로그에만 남긴다 — 응답에 실으면 설정값이 드러난다.
            log.warn("카카오 토큰 교환 실패", e);
            throw new UnauthorizedException("카카오 로그인에 실패했습니다. 다시 시도해주세요.");
        }

        if (res == null || res.accessToken() == null) {
            log.warn("카카오가 access_token 을 주지 않았습니다: {}", res);
            throw new UnauthorizedException("카카오 로그인에 실패했습니다. 다시 시도해주세요.");
        }
        return res.accessToken();
    }

    private KakaoUser fetchUser(String accessToken) {
        try {
            return rest.get()
                    .uri(USER_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUser.class);
        } catch (RestClientException e) {
            log.warn("카카오 사용자 조회 실패", e);
            throw new UnauthorizedException("카카오 로그인에 실패했습니다. 다시 시도해주세요.");
        }
    }

    /**
     * 닉네임은 두 군데에 올 수 있다.
     * 동의항목을 어떻게 설정했는지에 따라 profile 쪽에만 오기도 하고
     * properties 쪽에만 오기도 한다. 없으면 null 을 돌려주고,
     * 이름은 Member 쪽에서 「회원」으로 메운다.
     */
    private static String nicknameOf(KakaoUser user) {
        KakaoAccount account = user.kakaoAccount();
        if (account != null && account.profile() != null && account.profile().nickname() != null) {
            return account.profile().nickname();
        }
        return (user.properties() == null) ? null : user.properties().nickname();
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record KakaoUser(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
            Properties properties
    ) {
    }

    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean isEmailValid,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            Profile profile
    ) {
    }

    private record Profile(String nickname) {
    }

    private record Properties(String nickname) {
    }
}
