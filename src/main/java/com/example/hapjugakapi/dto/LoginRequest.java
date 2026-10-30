package com.example.hapjugakapi.dto;

public record LoginRequest(
        String email,
        String password
) {
}