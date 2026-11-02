package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "구글 로그인 정보가 없습니다.")
        String idToken
) {
}
