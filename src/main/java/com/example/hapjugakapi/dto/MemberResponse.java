package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        String area,
        String bio
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getArea(),
                member.getBio()
        );
    }
}