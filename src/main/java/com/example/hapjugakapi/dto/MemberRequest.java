package com.example.hapjugakapi.dto;

public record MemberRequest(
        String email,
        String password,
        String nickname,
        String area,
        String bio
) {
}