package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;

public record ActivityLogRequest(
        String type,
        Part part,
        String detail,
        String targetName
) {
}