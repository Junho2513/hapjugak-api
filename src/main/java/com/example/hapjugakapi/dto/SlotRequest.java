package com.example.hapjugakapi.dto;

/**
 * 자리 관련 요청의 공통 본문.
 * - 참여:        openToHandover 만 사용
 * - 양도 표시:    openToHandover 만 사용
 * - 관리자 배치:  memberId(비우려면 null) + openToHandover
 */
public record SlotRequest(
        Long memberId,
        Boolean openToHandover
) {
    public boolean open() {
        return Boolean.TRUE.equals(openToHandover);
    }
}