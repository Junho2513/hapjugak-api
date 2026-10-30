package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.Slot;

public record SlotResponse(
        Long id,
        Part part,
        Long memberId,
        String memberNickname,
        boolean taken
) {
    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getPart(),
                slot.isTaken() ? slot.getMember().getId() : null,
                slot.isTaken() ? slot.getMember().getNickname() : null,
                slot.isTaken()
        );
    }
}