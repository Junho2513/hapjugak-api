package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.Instrument;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;

import java.time.LocalDateTime;
import java.util.List;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        Role role,
        AuthProvider provider,
        String area,
        String bio,
        String photoUrl,
        List<Instrument> instruments,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getProvider(),
                member.getArea(),
                member.getBio(),
                member.getPhotoForDisplay(),   // 이관 전이면 옛 base64, 후면 S3 주소
                List.copyOf(member.getInstruments()),
                member.getCreatedAt()
        );
    }
}