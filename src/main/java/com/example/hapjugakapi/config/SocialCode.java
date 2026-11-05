package com.example.hapjugakapi.config;

/**
 * 브라우저가 제공자에게서 받아온 것들. 코드 하나로는 교환이 안 되는 곳이 있어 함께 묶는다.
 *
 * redirect_uri 는 여기 없다. 제공자에게 보낼 그 값은 각 클라이언트가 자기 설정에서
 * 읽는다. 브라우저가 보내준 값을 그대로 제공자에게 넘기면, 바깥에서 들어온 문자열이
 * 우리 요청의 일부가 된다. 제공자도 등록된 주소와 대조하므로 당장 뚫리진 않지만,
 * 안 받아도 되는 입력은 안 받는 쪽이 낫다.
 *
 * @param code  인가 코드. 한 번만 쓸 수 있고 몇 분 뒤 만료된다.
 * @param state 네이버가 토큰 교환에 요구한다. 구글과 카카오는 쓰지 않는다.
 *              값이 맞는지 보는 것은 브라우저 쪽이다 — 띄울 때 만든 값을
 *              sessionStorage 에 넣어두고, 돌아온 값과 같은지 본 뒤에야 서버로 보낸다.
 */
public record SocialCode(String code, String state) {
}
