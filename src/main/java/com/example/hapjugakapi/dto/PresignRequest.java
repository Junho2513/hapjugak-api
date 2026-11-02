package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.UploadKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 업로드 서명을 요청할 때 브라우저가 보내는 내용.
 * 파일 자체는 서버로 오지 않는다. 용도와 타입, 크기만 온다.
 *
 * 세 값은 모두 서명에 들어가므로 하나라도 비면 서명을 만들 수 없다.
 * 용도별 최대 크기 검사는 StorageService 가 따로 한다.
 */
public record PresignRequest(
        @NotNull(message = "업로드 용도가 필요합니다.")
        UploadKind kind,

        @NotBlank(message = "파일 형식이 필요합니다.")
        String contentType,

        @NotNull(message = "파일 크기가 필요합니다.")
        @Positive(message = "파일 크기가 올바르지 않습니다.")
        Long size
) {
}
