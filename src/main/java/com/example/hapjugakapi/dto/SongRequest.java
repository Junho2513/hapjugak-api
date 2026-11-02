package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 곡 등록(POST)과 수정(PATCH)에 함께 쓴다. */
public record SongRequest(
        @NotBlank(groups = OnCreate.class, message = "곡 제목을 입력해주세요.")
        @Size(max = 255, message = "곡 제목이 너무 깁니다.")
        String title,

        @Size(max = 255, message = "아티스트명이 너무 깁니다.")
        String artist,

        @Size(max = 500, message = "유튜브 주소가 너무 깁니다.")
        String youtubeUrl,

        @Size(max = 1000, message = "설명은 1000자까지 쓸 수 있습니다.")
        String description,

        @Size(max = 8, message = "파트는 8개까지 고를 수 있습니다.")
        List<Part> parts
) {
}
