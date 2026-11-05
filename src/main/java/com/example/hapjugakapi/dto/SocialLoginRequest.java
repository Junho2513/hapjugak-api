package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 팝업에서 계정을 고르면 돌아오는 인가 코드.
 *
 * 전에는 ID 토큰을 그대로 받았다. 그때는 구글이 그린 버튼을 써야 했고,
 * 그 버튼에는 로그인된 계정이 그대로 노출됐다.
 * 코드를 받는 방식으로 바꾸면서 버튼을 우리가 그릴 수 있게 됐고,
 * 카카오·네이버와 같은 모양으로 맞출 수 있게 됐다.
 *
 * @param code  인가 코드. 한 번만 쓸 수 있고 몇 분 뒤 만료된다.
 * @param state 네이버만 쓴다. 띄울 때 브라우저가 만든 무작위 문자열이고,
 *              네이버가 그대로 돌려준 것을 브라우저가 대조한 뒤 함께 보낸다.
 *              구글·카카오는 비워 보낸다.
 */
public record SocialLoginRequest(
        @NotBlank(message = "로그인 정보가 없습니다.")
        @Size(max = 2000, message = "로그인 정보가 너무 깁니다.")
        String code,

        @Size(max = 200, message = "state 가 너무 깁니다.")
        String state
) {
}
