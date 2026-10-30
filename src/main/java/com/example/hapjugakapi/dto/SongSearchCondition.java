package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;

public record SongSearchCondition(
        String keyword,
        Part openPart,
        Boolean candidateOnly,
        Long eventId
) {
}