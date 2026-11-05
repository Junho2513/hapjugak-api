package com.example.hapjugakapi.config;

/**
 * 소셜 제공자가 알려준 사람. 구글·카카오·네이버의 답을 같은 모양으로 맞춘 것이다.
 *
 * @param providerId  그 제공자 안에서의 고유 번호. 바뀌지 않는다.
 *                    구글은 sub, 카카오는 id, 네이버는 response.id 다.
 * @param email       이메일. <b>없을 수 있다.</b> 카카오와 네이버는 이메일 제공이
 *                    선택 동의라, 사용자가 거부하면 아예 오지 않는다.
 * @param emailVerified
 *        제공자가 "이 주소가 그 사람 것임을 우리가 확인했다"고 말하는가.
 *
 *        이 칸 하나가 이 기능에서 제일 중요하다. 전에는 소셜이 말하는 이메일만 보고
 *        같은 주소의 기존 계정에 그대로 로그인시켰다. 구글만 있을 때는 큰 문제가
 *        아니었다 — 구글은 이메일을 확인하고 준다.
 *
 *        카카오는 다르다. 카카오계정의 이메일은 미인증일 수 있고, 응답에
 *        is_email_verified: false 로 온다. 그걸 그대로 믿으면, 공격자가 자기
 *        카카오 계정의 이메일을 남의 주소로 바꾸는 것만으로 그 사람 계정에 들어간다.
 *
 *        그래서 이메일로 기존 계정을 잇는 것은 이 값이 true 일 때만 한다.
 * @param nickname    표시할 이름. 없으면 null.
 */
public record SocialUser(
        String providerId,
        String email,
        boolean emailVerified,
        String nickname
) {

    /** 이메일로 기존 계정을 이어도 되는가. 주소가 있고, 제공자가 확인했을 때만이다. */
    public boolean hasTrustedEmail() {
        return email != null && !email.isBlank() && emailVerified;
    }
}
