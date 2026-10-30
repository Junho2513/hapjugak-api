package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        Role role,
        String area,
        String bio
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getArea(),
                member.getBio()
        );
    }
}