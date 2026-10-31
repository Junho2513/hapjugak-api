package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Part;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        LocalDate date,
        LocalDate deadline,
        LocalTime timeStart,
        LocalTime timeEnd,
        String region,
        String studio,
        String room,
        String place,
        String genre,
        String fee,
        String description,
        String afterparty,
        int capacity,
        int maxSongsPerUser,
        Event.Visibility visibility,
        List<Part> defaultParts,
        String coverUrl,
        Long hostId,
        String hostNickname,
        boolean completed,
        boolean deadlinePassed,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDate(),
                event.getDeadline(),
                event.getTimeStart(),
                event.getTimeEnd(),
                event.getRegion(),
                event.getStudio(),
                event.getRoom(),
                event.getPlace(),
                event.getGenre(),
                event.getFee(),
                event.getDescription(),
                event.getAfterparty(),
                event.getCapacity(),
                event.getMaxSongsPerUser(),
                event.getVisibility(),
                List.copyOf(event.getDefaultParts()),
                event.getCoverUrl(),
                event.getHost().getId(),
                event.getHost().getNickname(),
                event.isCompleted(),
                event.isDeadlinePassed(),
                event.getCompletedAt(),
                event.getCreatedAt()
        );
    }
}