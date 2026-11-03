package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 비공개 일정의 주소를 추측할 수 없는지 본다.
 *
 * 목록에서 가리는 것만으로는 부족했다. 주소가 /events/1, /events/2 … 순서라서
 * 1부터 눌러보면 비공개 일정이 전부 나왔다. 가린 것이 무의미했다.
 */
@SpringBootTest
@Transactional
class InviteKeyTest {

    @Autowired private EventService eventService;
    @Autowired private SongService songService;
    @Autowired private EventRepository eventRepository;
    @Autowired private MemberRepository memberRepository;

    private Member 주최자;
    private Member 참가자;
    private Member 남;

    private Event 공개일정;
    private Event 비공개일정;

    @BeforeEach
    void 준비() {
        주최자 = memberRepository.save(new Member("host@test.com", "pw", "주최자", "서울", ""));
        참가자 = memberRepository.save(new Member("player@test.com", "pw", "참가자", "서울", ""));
        남 = memberRepository.save(new Member("stranger@test.com", "pw", "남", "부산", ""));

        공개일정 = 일정만들기("누구나", Event.Visibility.PUBLIC);
        비공개일정 = 일정만들기("아는 사람만", Event.Visibility.PRIVATE);
    }

    @Test
    @DisplayName("공개 일정은 키 없이 열린다")
    void 공개는_그냥() {
        assertDoesNotThrow(() -> eventService.findById(공개일정.getId(), null, Viewer.anonymous()));
    }

    @Test
    @DisplayName("비공개 일정은 키 없이 열리지 않는다 — id 를 찍어봐도 소용없다")
    void 키가_없으면() {
        assertThrows(NotFoundException.class,
                () -> eventService.findById(비공개일정.getId(), null, Viewer.anonymous()));
    }

    @Test
    @DisplayName("틀린 키도 막는다")
    void 키가_틀리면() {
        assertThrows(NotFoundException.class,
                () -> eventService.findById(비공개일정.getId(), "AAAAAAAAAAAAAAAA", Viewer.anonymous()));
    }

    @Test
    @DisplayName("없는 일정과 키가 틀린 일정은 똑같이 404다")
    void 구분되지_않는다() {
        // 403 이면 "여기 뭔가 있다"를 알려준다. 그것만으로 훑어서 지도를 그릴 수 있다.
        NotFoundException 없는것 = assertThrows(NotFoundException.class,
                () -> eventService.findById(999_999L, null, Viewer.anonymous()));
        NotFoundException 틀린키 = assertThrows(NotFoundException.class,
                () -> eventService.findById(비공개일정.getId(), "AAAAAAAAAAAAAAAA", Viewer.anonymous()));

        assertEquals(없는것.getMessage(), 틀린키.getMessage());
    }

    @Test
    @DisplayName("키가 맞으면 로그인하지 않아도 열린다 — 그게 초대다")
    void 키가_맞으면() {
        String key = 비공개일정.getInviteKey();

        assertDoesNotThrow(() -> eventService.findById(비공개일정.getId(), key, Viewer.anonymous()));
    }

    @Test
    @DisplayName("주최자는 키 없이도 연다")
    void 주최자는() {
        assertDoesNotThrow(() ->
                eventService.findById(비공개일정.getId(), null, new Viewer(주최자.getId(), false)));
    }

    @Test
    @DisplayName("관리자는 키 없이도 연다")
    void 관리자는() {
        assertDoesNotThrow(() ->
                eventService.findById(비공개일정.getId(), null, new Viewer(남.getId(), true)));
    }

    @Test
    @DisplayName("자리를 잡은 참가자는 키 없이도 연다 — 링크를 잃어버렸다고 자기 합주를 못 보면 안 된다")
    void 참가자는() {
        자리잡기(비공개일정, 참가자);

        assertDoesNotThrow(() ->
                eventService.findById(비공개일정.getId(), null, new Viewer(참가자.getId(), false)));
    }

    @Test
    @DisplayName("상관없는 회원은 로그인했어도 못 연다")
    void 남은_못_연다() {
        assertThrows(NotFoundException.class,
                () -> eventService.findById(비공개일정.getId(), null, new Viewer(남.getId(), false)));
    }

    @Test
    @DisplayName("곡 목록도 같은 규칙을 따른다 — 일정만 막고 곡이 새면 의미가 없다")
    void 곡_목록도() {
        assertThrows(NotFoundException.class,
                () -> songService.findByEvent(비공개일정.getId(), null, Viewer.anonymous()));

        assertDoesNotThrow(() ->
                songService.findByEvent(비공개일정.getId(), 비공개일정.getInviteKey(), Viewer.anonymous()));
    }

    @Test
    @DisplayName("공개였다가 비공개로 바꾸면 키가 새로 뽑힌다")
    void 비공개로_바꾸면_키가_바뀐다() {
        // 공개 시절의 주소는 이미 퍼졌을 수 있다. 그대로 두면 숨긴 뜻이 없다.
        String 옛키 = 공개일정.getInviteKey();

        공개일정.setVisibility(Event.Visibility.PRIVATE);

        assertNotEquals(옛키, 공개일정.getInviteKey());
        assertThrows(NotFoundException.class,
                () -> eventService.findById(공개일정.getId(), 옛키, Viewer.anonymous()));
    }

    @Test
    @DisplayName("키는 16자이고 매번 다르다")
    void 키_모양() {
        Set<String> 키들 = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String key = 일정만들기("합주 " + i, Event.Visibility.PRIVATE).getInviteKey();
            assertEquals(16, key.length(), "주소에 쓰는 16자여야 한다");
            assertTrue(key.matches("[A-Za-z0-9_-]{16}"), "주소에 그대로 쓸 수 있는 글자만 나와야 한다: " + key);
            키들.add(key);
        }
        assertEquals(50, 키들.size(), "같은 키가 두 번 나오면 난수가 아니다");
    }

    private Event 일정만들기(String 제목, Event.Visibility 공개여부) {
        Event event = new Event(제목, LocalDate.now().plusDays(30), LocalDate.now().plusDays(7), "홍대", 주최자);
        event.setVisibility(공개여부);
        return eventRepository.save(event);
    }

    private void 자리잡기(Event event, Member member) {
        SongResponse song = songService.createIn(event.getId(),
                new SongRequest("참가 확인용", "아티스트", null, null, List.of(Part.VOCAL)), 주최자.getId());
        songService.claimSlot(song.id(), song.slots().get(0).id(), member.getId(), false);
    }
}
