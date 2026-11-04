package com.example.hapjugakapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

        /**
         * 모집할 자리의 <b>이름</b> 목록. 「보컬1」, 「기타2」, 「색소폰」처럼 적은 그대로다.
         *
         * <p>전에는 정해진 8가지 중에서만 고를 수 있었다(enum). 그래서 보컬이 둘인 곡도,
         * 색소폰이 들어가는 곡도 적을 수가 없었다. 이제 이름을 받는다.
         *
         * <p>이름만 받으면 아무 글자나 들어올 수 있으므로 길이와 글자를 제한한다.
         * 줄바꿈이나 꺾쇠가 섞여 들어오면 화면이 깨진다.
         */
        @Size(max = 12, message = "파트는 12개까지 만들 수 있습니다.")
        List<
                @NotBlank(message = "파트 이름을 비워둘 수 없습니다.")
                @Size(max = 20, message = "파트 이름은 20자까지 쓸 수 있습니다.")
                @Pattern(regexp = "[^<>\\r\\n\\t]+", message = "파트 이름에 쓸 수 없는 글자가 있습니다.")
                String> parts
) {
}
