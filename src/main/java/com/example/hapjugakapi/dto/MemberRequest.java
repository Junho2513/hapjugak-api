package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Instrument;

import java.util.List;

public record MemberRequest(
        String email,
        String password,
        String nickname,
        String area,
        String bio,
        String photoUrl,
        List<Instrument> instruments
) {
}