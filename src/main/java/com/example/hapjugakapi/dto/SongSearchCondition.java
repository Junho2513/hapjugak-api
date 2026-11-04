package com.example.hapjugakapi.dto;

public record SongSearchCondition(
        String keyword,
        /** 비어 있는 자리의 이름. 「베이스」처럼 적으면 「베이스2」도 함께 걸린다. */
        String openPart,
        Boolean candidateOnly,
        Long eventId
) {
}