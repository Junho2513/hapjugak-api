package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;

import java.util.List;

public record SongRequest(
        String title,
        String artist,
        String youtubeUrl,
        String description,
        Long proposerId,
        List<Part> parts
) {
}