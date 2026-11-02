package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호에 길이 제한을 걸지 않는다.
 *
 * 가입 때는 8자 이상을 요구하지만, 그 규칙이 생기기 전에 가입한 회원이 있다.
 * 로그인 쪽에 같은 제한을 걸면 그 사람들이 들어오지 못한다.
 * 여기서 확인할 것은 "형식이 맞는가"가 아니라 "비어 있지 않은가"뿐이다.
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
