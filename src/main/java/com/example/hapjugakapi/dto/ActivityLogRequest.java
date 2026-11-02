package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivityLogRequest(
        @NotBlank(message = "기록 종류가 필요합니다.")
        @Size(max = 30, message = "기록 종류가 너무 깁니다.")
        String type,

        Part part,

        @Size(max = 200, message = "기록 내용은 200자까지 쓸 수 있습니다.")
        String detail,

        @Size(max = 20, message = "대상 이름이 너무 깁니다.")
        String targetName
) {
}
