package com.example.hapjugakapi.dto;

public record SongSearchCondition(
        String keyword,
        /** 비어 있는 자리의 이름. 「베이스」처럼 적으면 「베이스2」도 함께 걸린다. */
        String openPart,
        /*
         * candidateOnly 가 여기 있었다. 「아직 일정에 안 들어간 곡만」을 거르던 조건인데,
         * V8 이 song.event_id 를 NOT NULL 로 조이면서 그런 곡이 생길 수 없게 되었다.
         * 남겨두면 아무것도 걸리지 않는 조건이 된다.
         */
        Long eventId
) {
}