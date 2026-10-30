package com.example.hapjugakapi.dto;

import java.time.LocalDate;

public record EventRequest(
        String title,
        LocalDate date,
        LocalDate deadline,
        String place
) {
}