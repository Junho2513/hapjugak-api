package com.example.hapjugakapi.config;

import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글이 준 인가 코드를 ID 토큰으로 바꾼다.
 *
 * 왜 코드를 거치는가 —
 * 구글이 직접 그려주는 버튼(GIS)을 쓰면 ID 토큰이 브라우저로 바로 오지만,
 * 그 버튼의 생김새는 구글이 정한다. 로그인한 계정이 버튼에 그대로 드러나고,
 * 카카오·네이버 버튼과 나란히 두면 혼자 다르게 보인다.
 *
 * 인가 코드 방식은 버튼을 우리가 그린다. 누르면 구글 팝업이 뜨고,
 * 사용자가 계정을 고르면 짧은 코드가 돌아온다.
 * 그 코드는 그 자체로는 아무것도 아니고, 여기서 client_secret 과 함께
 * 구글에 되물어야 ID 토큰이 된다. 그래서 코드가 새어도 바로 악용되지 않는다.
 *
 * 카카오와 네이버도 같은 방식이다. 주소와 키만 다르다.
 */
@Component
public class GoogleOAuthClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    /**
     * 팝업(ux_mode: popup)으로 받은 코드를 교환할 때는 redirect_uri 자리에
     * 이 약속된 문자열을 넣는다. 실제 주소가 아니다.
     */
    private static final String POPUP_REDIRECT = "postmessage";

    private final RestClient rest = SocialHttp.client();
    private final GoogleTokenVerifier verifier;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthClient(GoogleTokenVerifier verifier,
                             @Value("${google.client-id}") String clientId,
                             @Value("${google.client-secret}") String clientSecret) {
        this.verifier = verifier;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    /**
     * 구글만 사람 정보를 따로 묻지 않는다.
     *
     * 돌려받은 ID 토큰 안에 이메일·이름이 이미 들어 있어서, 서명만 확인하면
     * 요청을 한 번 덜 보내도 된다. 카카오·네이버는 ID 토큰이 없거나 쓰지 않으므로
     * 접근 토큰을 들고 사람 정보를 묻는 길로 간다.
     */
    @Override
    public SocialUser exchange(SocialCode code) {
        return verifier.verify(exchangeForIdToken(code));
    }

    private String exchangeForIdToken(SocialCode code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code.code());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", POPUP_REDIRECT);
        form.add("grant_type", "authorization_code");

        TokenResponse res;
        try {
            res = rest.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // 코드가 이미 쓰였거나, 만료됐거나, client_secret 이 틀린 경우다.
            // 자세한 내용은 로그에만 남긴다 - 응답에 실으면 설정값이 드러난다.
            log.warn("구글 토큰 교환 실패", e);
            throw new UnauthorizedException("구글 로그인에 실패했습니다. 다시 시도해주세요.");
        }

        if (res == null || res.idToken() == null) {
            log.warn("구글이 id_token 을 주지 않았습니다: {}", res);
            throw new UnauthorizedException("구글 로그인에 실패했습니다. 다시 시도해주세요.");
        }
        return res.idToken();
    }

    /** 구글 응답 중 우리가 쓰는 건 id_token 하나뿐이다. */
    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("id_token") String idToken
    ) {
    }
}
