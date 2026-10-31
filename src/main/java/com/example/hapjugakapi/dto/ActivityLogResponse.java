package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.ActivityLog;
import com.example.hapjugakapi.domain.Part;

import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        String type,
        Long actorId,
        String actorName,
        String songTitle,
        Part part,
        String detail,
        String targetName,
        LocalDateTime createdAt
) {
    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getType(),
                log.getActor() == null ? null : log.getActor().getId(),
                log.getActorName(),
                log.getSongTitle(),
                log.getPart(),
                log.getDetail(),
                log.getTargetName(),
                log.getCreatedAt()
        );
    }
}