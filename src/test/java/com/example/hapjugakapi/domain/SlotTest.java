package com.example.hapjugakapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlotTest {

    private Member member(String nickname) {
        return new Member(nickname + "@test.com", "pw", nickname, "서울", "");
    }

    private Slot drumSlot() {
        Song song = new Song("곡", "가수", null, null, member("제안자"));
        return new Slot(song, Part.DRUM);
    }

    @Test
    @DisplayName("새로 만든 자리는 비어 있다")
    void 새_자리는_비어있다() {
        Slot slot = drumSlot();

        assertFalse(slot.isTaken());
        assertNull(slot.getMember());
    }

    @Test
    @DisplayName("자리를 잡으면 점유 상태가 된다")
    void 자리_잡기() {
        Slot slot = drumSlot();
        Member drummer = member("드러머");

        slot.claimBy(drummer);

        assertTrue(slot.isTaken());
        assertEquals("드러머", slot.getMember().getNickname());
    }

    @Test
    @DisplayName("이미 찬 자리는 다른 사람이 잡을 수 없다")
    void 중복_참여_불가() {
        Slot slot = drumSlot();
        slot.claimBy(member("먼저온사람"));

        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> slot.claimBy(member("나중에온사람"))
        );

        assertEquals("이미 찬 자리입니다.", e.getMessage());
    }

    @Test
    @DisplayName("자리를 빼면 다시 비어 있는 상태가 된다")
    void 자리_빼기() {
        Slot slot = drumSlot();
        slot.claimBy(member("드러머"));

        slot.release();

        assertFalse(slot.isTaken());
    }
}