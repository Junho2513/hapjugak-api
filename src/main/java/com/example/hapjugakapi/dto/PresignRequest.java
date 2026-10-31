package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.UploadKind;

/**
 * 업로드 서명을 요청할 때 브라우저가 보내는 내용.
 * 파일 자체는 서버로 오지 않는다. 용도와 타입, 크기만 온다.
 */
public record PresignRequest(
        UploadKind kind,
        String contentType,
        Long size
) {
}