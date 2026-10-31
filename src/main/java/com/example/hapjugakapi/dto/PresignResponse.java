package com.example.hapjugakapi.dto;

/**
 * uploadUrl — 브라우저가 이 주소로 파일을 PUT 한다. 몇 분 뒤 만료된다.
 * publicUrl — 업로드가 끝나면 이 주소로 영구히 읽을 수 있다. DB에 저장되는 건 이쪽.
 */
public record PresignResponse(
        String key,
        String uploadUrl,
        String publicUrl
) {
}