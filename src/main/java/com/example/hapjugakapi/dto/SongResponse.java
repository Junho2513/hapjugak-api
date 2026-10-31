package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Song;

import java.time.LocalDateTime;
import java.util.List;

public record SongResponse(
        Long id,
        String title,
        String artist,
        String youtubeUrl,
        String description,
        Long proposerId,
        String proposerNickname,
        Long eventId,
        boolean candidate,
        boolean complete,
        long filledCount,
        int totalCount,
        LocalDateTime createdAt,
        List<SlotResponse> slots
) {
    public static SongResponse from(Song song) {
        return new SongResponse(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.getYoutubeUrl(),
                song.getDescription(),
                song.getProposer().getId(),
                song.getProposer().getNickname(),
                song.getEvent() == null ? null : song.getEvent().getId(),
                song.isCandidate(),
                song.isComplete(),
                song.getFilledCount(),
                song.getSlots().size(),
                song.getCreatedAt(),
                song.getSlots().stream().map(SlotResponse::from).toList()
        );
    }
}