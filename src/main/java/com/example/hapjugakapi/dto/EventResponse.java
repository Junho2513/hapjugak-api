package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Event;

import java.time.LocalDate;

public record EventResponse(
        Long id,
        String title,
        LocalDate date,
        LocalDate deadline,
        String place,
        Long hostId,
        String hostNickname,
        boolean completed,
        boolean deadlinePassed
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDate(),
                event.getDeadline(),
                event.getPlace(),
                event.getHost().getId(),
                event.getHost().getNickname(),
                event.isCompleted(),
                event.isDeadlinePassed()
        );
    }
}