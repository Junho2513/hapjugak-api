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
        /** 자리가 다 찬 순간. 화면은 성립한 곡을 이 순서로 줄 세웁니다(먼저 성립한 곡이 위). */
        LocalDateTime completedAt,
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
                song.getCompletedAt(),
                song.getSlots().stream().map(SlotResponse::from).toList()
        );
    }
}