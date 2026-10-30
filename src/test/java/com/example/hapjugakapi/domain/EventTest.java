package com.example.hapjugakapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private Member host() {
        return new Member("host@test.com", "pw", "주최자", "서울", "");
    }

    private Event eventWithDeadline(LocalDate deadline) {
        return new Event("합주", LocalDate.now().plusDays(30), deadline, "홍대", host());
    }

    @Test
    @DisplayName("마감일이 지나지 않았으면 false")
    void 마감_전() {
        Event event = eventWithDeadline(LocalDate.now().plusDays(1));

        assertFalse(event.isDeadlinePassed());
    }

    @Test
    @DisplayName("마감일이 지났으면 true")
    void 마감_후() {
        Event event = eventWithDeadline(LocalDate.now().minusDays(1));

        assertTrue(event.isDeadlinePassed());
    }

    @Test
    @DisplayName("마감일 당일은 아직 마감이 아니다")
    void 마감일_당일() {
        Event event = eventWithDeadline(LocalDate.now());

        assertFalse(event.isDeadlinePassed());
    }

    @Test
    @DisplayName("완료 처리하면 completed가 true가 된다")
    void 합주_완료() {
        Event event = eventWithDeadline(LocalDate.now().plusDays(1));

        assertFalse(event.isCompleted());
        event.complete();
        assertTrue(event.isCompleted());
    }
}