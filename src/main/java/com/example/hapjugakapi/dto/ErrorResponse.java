package com.example.hapjugakapi.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, message);
    }
}