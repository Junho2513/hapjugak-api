package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.SongSearchCondition;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SongServiceTest {

    @Autowired
    private SongService songService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    private Member 기타리스트;
    private Member 드러머;

    @BeforeEach
    void setUp() {
        기타리스트 = memberRepository.save(
                new Member("guitarist@test.com", "pw", "기타리스트", "서울", ""));
        드러머 = memberRepository.save(
                new Member("drummer@test.com", "pw", "드러머", "경기", ""));
    }

    private Event 일정만들기(LocalDate deadline) {
        return eventRepository.save(
                new Event("합주", LocalDate.now().plusDays(30), deadline, "홍대", 기타리스트)
        );
    }

    private SongResponse 곡만들기() {
        return songService.create(
                new SongRequest("廻廻奇譚", "Eve", null, null, List.of(Part.VOCAL, Part.DRUM)),
                기타리스트.getId()
        );
    }

    @Test
    @DisplayName("곡을 등록하면 요청한 파트 수만큼 자리가 생긴다")
    void 곡_등록() {
        SongResponse song = 곡만들기();

        assertEquals(2, song.totalCount());
        assertEquals(0, song.filledCount());
        assertTrue(song.candidate());
        assertEquals("기타리스트", song.proposerNickname());
    }

    @Test
    @DisplayName("마감이 지난 일정에는 곡을 배정할 수 없다")
    void 마감된_일정_배정_실패() {
        SongResponse song = 곡만들기();
        Event 마감된일정 = 일정만들기(LocalDate.now().minusDays(1));

        ConflictException e = assertThrows(
                ConflictException.class,
                () -> songService.assignToEvent(song.id(), 마감된일정.getId())
        );

        assertEquals("곡 신청이 마감된 일정입니다.", e.getMessage());
    }

    @Test
    @DisplayName("마감 전 일정에는 곡을 배정할 수 있다")
    void 열린_일정_배정_성공() {
        SongResponse song = 곡만들기();
        Event 열린일정 = 일정만들기(LocalDate.now().plusDays(7));

        SongResponse 배정됨 = songService.assignToEvent(song.id(), 열린일정.getId());

        assertFalse(배정됨.candidate());
        assertEquals(열린일정.getId(), 배정됨.eventId());
    }

    @Test
    @DisplayName("본인이 잡지 않은 자리는 뺄 수 없다")
    void 남의_자리_빼기_실패() {
        SongResponse song = 곡만들기();
        Long slotId = song.slots().get(0).id();

        songService.claimSlot(song.id(), slotId, 기타리스트.getId());

        assertThrows(
                ForbiddenException.class,
                () -> songService.releaseSlot(song.id(), slotId, 드러머.getId())
        );
    }

    @Test
    @DisplayName("모든 자리가 차면 성립 상태가 된다")
    void 곡_성립() {
        SongResponse song = 곡만들기();

        songService.claimSlot(song.id(), song.slots().get(0).id(), 기타리스트.getId());
        SongResponse 완성 = songService.claimSlot(song.id(), song.slots().get(1).id(), 드러머.getId());

        assertTrue(완성.complete());
        assertEquals(2, 완성.filledCount());
    }

    @Test
    @DisplayName("검색 — 비어 있는 파트로 거를 수 있다")
    void 파트_검색() {
        SongResponse song = 곡만들기();
        songService.claimSlot(song.id(), song.slots().get(0).id(), 기타리스트.getId());

        List<SongResponse> 드럼_빈곡 = songService.search(
                new SongSearchCondition(null, Part.DRUM, null, null));
        List<SongResponse> 보컬_빈곡 = songService.search(
                new SongSearchCondition(null, Part.VOCAL, null, null));

        assertEquals(1, 드럼_빈곡.size());
        assertEquals(0, 보컬_빈곡.size());
    }
}