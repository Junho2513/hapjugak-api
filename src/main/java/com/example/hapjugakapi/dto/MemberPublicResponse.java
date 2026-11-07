package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Member;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 남이 보는 프로필.
 *
 * MemberResponse 와 나눈 이유는 하나다 — **이메일을 내보내지 않기 위해서다.**
 *
 * 하나의 DTO 로 두 쓰임을 겸하면, 언젠가 "여기선 이메일을 빼자"를 코드 곳곳에서
 * 기억해야 한다. 한 번 잊으면 그대로 샌다. 타입을 나눠두면 잊을 수가 없다.
 *
 * 역할(role)과 가입 방식(provider)도 뺐다. 남이 알 필요가 없고,
 * "누가 관리자인지"는 공격자에게 과녁을 알려주는 정보다.
 *
 * 본인 정보는 GET /members/me 가 MemberResponse 로 돌려준다.
 */
public record MemberPublicResponse(
        Long id,
        String nickname,
        String area,
        String bio,
        String photoUrl,
        List<String> instruments,
        LocalDateTime createdAt
) {
    public static MemberPublicResponse from(Member member) {
        return new MemberPublicResponse(
                member.getId(),
                member.getNickname(),
                member.getArea(),
                member.getBio(),
                member.getPhotoUrl(),
                List.copyOf(member.getInstruments()),
                member.getCreatedAt()
        );
    }
}
