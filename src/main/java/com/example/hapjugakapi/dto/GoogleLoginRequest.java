package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 팝업에서 계정을 고르면 돌아오는 인가 코드.
 *
 * 전에는 ID 토큰을 그대로 받았다. 그때는 구글이 그린 버튼을 써야 했고,
 * 그 버튼에는 로그인된 계정이 그대로 노출됐다.
 * 코드를 받는 방식으로 바꾸면서 버튼을 우리가 그릴 수 있게 됐고,
 * 카카오·네이버와 같은 모양으로 맞출 수 있게 됐다.
 */
public record GoogleLoginRequest(
        @NotBlank(message = "구글 로그인 정보가 없습니다.")
        String code
) {
}
