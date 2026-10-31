package com.example.hapjugakapi.domain;

/**
 * 업로드 용도. 용도마다 저장 경로와 크기 상한이 다르다.
 *
 * 상한을 서버가 들고 있는 이유 — 브라우저에서 이미 리사이즈·압축해서 보내지만,
 * 그 과정을 건너뛰고 API를 직접 호출하는 건 누구나 할 수 있다.
 * 클라이언트의 선의를 신뢰하는 검증은 검증이 아니다.
 */
public enum UploadKind {

    PROFILE("profile", 300 * 1024),        // 프로필 사진 300KB
    COVER("cover", 1024 * 1024),           // 합주 커버 1MB
    HERO("hero", 2 * 1024 * 1024);         // 사이트 대표 이미지 2MB

    private final String prefix;
    private final long maxBytes;

    UploadKind(String prefix, long maxBytes) {
        this.prefix = prefix;
        this.maxBytes = maxBytes;
    }

    public String getPrefix() {
        return prefix;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public String getMaxLabel() {
        return (maxBytes / 1024) + "KB";
    }
}