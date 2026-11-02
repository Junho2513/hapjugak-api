package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Part;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 생성과 수정에 함께 쓴다.
 * 수정(PATCH)에서는 null인 항목을 "바꾸지 않음"으로 해석하므로,
 * 숫자와 참·거짓도 int/boolean이 아닌 Integer/Boolean으로 받는다.
 */
public record EventRequest(
        @NotBlank(groups = OnCreate.class, message = "합주 이름을 입력해주세요.")
        @Size(max = 60, message = "합주 이름은 60자까지 쓸 수 있습니다.")
        String title,

        @NotNull(groups = OnCreate.class, message = "합주 날짜를 정해주세요.")
        LocalDate date,

        LocalDate deadline,
        LocalTime timeStart,
        LocalTime timeEnd,

        @Size(max = 30, message = "지역이 너무 깁니다.")
        String region,

        @Size(max = 100, message = "합주실 이름이 너무 깁니다.")
        String studio,

        @Size(max = 60, message = "방 이름이 너무 깁니다.")
        String room,

        @Size(max = 40, message = "장르가 너무 깁니다.")
        String genre,

        @Size(max = 200, message = "비용 안내가 너무 깁니다.")
        String fee,

        @Size(max = 2000, message = "설명은 2000자까지 쓸 수 있습니다.")
        String description,

        @Size(max = 40, message = "뒤풀이 안내가 너무 깁니다.")
        String afterparty,

        @Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
        @Max(value = 200, message = "정원은 200명까지 지정할 수 있습니다.")
        Integer capacity,

        @Min(value = 1, message = "한 사람당 곡 수는 1곡 이상이어야 합니다.")
        @Max(value = 50, message = "한 사람당 곡 수는 50곡까지 지정할 수 있습니다.")
        Integer maxSongsPerUser,

        Event.Visibility visibility,
        List<Part> defaultParts,

        @Size(max = 500, message = "이미지 주소가 너무 깁니다.")
        String coverUrl,

        Boolean completed
) {
}
