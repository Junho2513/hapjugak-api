package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Part;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 생성과 수정에 함께 쓴다.
 * 수정(PATCH)에서는 null인 항목을 "바꾸지 않음"으로 해석하므로,
 * 숫자와 참·거짓도 int/boolean이 아닌 Integer/Boolean으로 받는다.
 */
public record EventRequest(
        String title,
        LocalDate date,
        LocalDate deadline,
        LocalTime timeStart,
        LocalTime timeEnd,
        String region,
        String studio,
        String room,
        String genre,
        String fee,
        String description,
        String afterparty,
        Integer capacity,
        Integer maxSongsPerUser,
        Event.Visibility visibility,
        List<Part> defaultParts,
        String coverImage,
        Boolean completed
) {
}