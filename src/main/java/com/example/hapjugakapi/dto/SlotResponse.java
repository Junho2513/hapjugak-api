package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.Slot;

public record SlotResponse(
        Long id,
        /** 화면에 보이는 자리 이름. 「보컬1」, 「색소폰」. 화면은 이것으로 자리를 가린다. */
        String label,
        /** 악기 종류. 직접 적은 파트는 비어 있다. 악기가 겹치는지 볼 때만 쓴다. */
        Part part,
        Long memberId,
        String memberNickname,
        // 참가자 목록에 얼굴을 띄우기 위한 것이다. 공개 프로필에 이미 들어 있는 값이라
        // 여기서 함께 내보내도 새로 새는 정보가 없다. 메일이나 역할은 여전히 내보내지 않는다.
        String memberPhotoUrl,
        boolean taken,
        boolean openToHandover
) {
    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getLabel(),
                slot.getPart(),
                slot.isTaken() ? slot.getMember().getId() : null,
                slot.isTaken() ? slot.getMember().getNickname() : null,
                slot.isTaken() ? slot.getMember().getPhotoUrl() : null,
                slot.isTaken(),
                slot.isOpenToHandover()
        );
    }
}