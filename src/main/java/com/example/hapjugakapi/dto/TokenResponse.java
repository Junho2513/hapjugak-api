package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Role;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Long memberId,
        String nickname,
        Role role
) {
}