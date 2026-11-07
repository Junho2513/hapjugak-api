package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 가입(POST /members)과 내 정보 수정(PUT /members/me)에 함께 쓴다.
 *
 * 길이 제한은 전부 DB 컬럼 길이에 맞췄다.
 * 맞추지 않으면 DB 가 거부하면서 500 이 나가는데,
 * 사용자 입장에서는 "길어서 안 된다"는 걸 알 방법이 없다.
 */
public record MemberRequest(
        @NotBlank(groups = OnCreate.class, message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일이 너무 깁니다.")
        String email,

        @NotBlank(groups = OnCreate.class, message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(groups = OnCreate.class, message = "닉네임을 입력해주세요.")
        @Size(max = 20, message = "닉네임은 20자까지 쓸 수 있습니다.")
        String nickname,

        @Size(max = 255, message = "활동 지역이 너무 깁니다.")
        String area,

        @Size(max = 1000, message = "소개는 1000자까지 쓸 수 있습니다.")
        String bio,

        @Size(max = 500, message = "이미지 주소가 너무 깁니다.")
        String photoUrl,

        /* 자유 문자열이다. 길이와 개수는 서비스에서 다듬으며 거른다 —
           여기서 @Size 로 막으면 공백만 다른 값이 그대로 통과한다. */
        List<String> instruments
) {
}
