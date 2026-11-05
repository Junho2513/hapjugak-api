package com.example.hapjugakapi.config;

import com.example.hapjugakapi.domain.AuthProvider;

/**
 * 소셜 로그인 제공자 하나.
 *
 * 브라우저가 받아온 인가 코드를 "누구인지"로 바꾼다. 세 제공자가 하는 일은 같지만
 * 거치는 길이 다르다.
 *
 *   구글   코드 → ID 토큰 → 서명 검증 → 클레임을 읽는다
 *   카카오 코드 → 접근 토큰 → kapi.kakao.com 에 사람 정보를 묻는다
 *   네이버 코드 → 접근 토큰 → openapi.naver.com 에 사람 정보를 묻는다
 *
 * 구글만 서명을 검증하는 것이 이상해 보일 수 있다. 네이버는 OIDC 를 지원하지 않아
 * ID 토큰이라는 것 자체가 없다. 그런데 서명 검증이 꼭 필요한 것도 아니다.
 * 서명은 "이 토큰이 정말 제공자가 만든 것인가"를 확인하는 수단인데,
 * 우리가 TLS 로 제공자에게 직접 물어서 받은 답이라면 그 질문이 이미 끝나 있다.
 *
 * 구글에서 서명을 검증하는 이유는 따로 있다. ID 토큰에 이메일·이름이 다 들어 있어서
 * 사람 정보를 묻는 요청을 한 번 더 보내지 않아도 된다. 요청 한 번을 아끼는 대신
 * 검증을 하는 쪽이다.
 */
public interface SocialClient {

    /** 어느 제공자인가. AuthService 가 이걸로 구현체를 고른다. */
    AuthProvider provider();

    /**
     * 인가 코드를 사람 정보로 바꾼다.
     *
     * @throws com.example.hapjugakapi.exception.UnauthorizedException
     *         코드가 이미 쓰였거나, 만료됐거나, 키가 틀렸을 때
     */
    SocialUser exchange(SocialCode code);
}
