package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.SongSearchCondition;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「비공개 — 링크로만 참가」가 실제로 무언가를 막는지 본다.
 *
 * 전에는 목록이 공개·비공개를 구분하지 않고 전부 돌려줬다.
 * 설정 화면에는 선택지가 있는데 아무 일도 일어나지 않았으므로,
 * 기능이 없는 것보다 나빴다 — 사용자는 가려졌다고 믿고 있었다.
 */
@SpringBootTest
@Transactional
class EventVisibilityTest {

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

        공개일정 = 일정만들기("누구나 오는 합주", Event.Visibility.PUBLIC);
        비공개일정 = 일정만들기("아는 사람만", Event.Visibility.PRIVATE);
    }

    @Test
    @DisplayName("로그인하지 않은 사람에게는 비공개 일정이 보이지 않는다")
    void 익명() {
        List<EventResponse> 목록 = eventService.find(null, Viewer.anonymous());

        assertTrue(들어있나(목록, 공개일정));
        assertFalse(들어있나(목록, 비공개일정));
    }

    @Test
    @DisplayName("상관없는 회원에게도 보이지 않는다 — 로그인만 하면 뚫리면 안 된다")
    void 남이_보면() {
        List<EventResponse> 목록 = eventService.find(null, new Viewer(남.getId(), false));

        assertFalse(들어있나(목록, 비공개일정));
    }

    @Test
    @DisplayName("주최자 본인에게는 보인다")
    void 주최자가_보면() {
        List<EventResponse> 목록 = eventService.find(null, new Viewer(주최자.getId(), false));

        assertTrue(들어있나(목록, 비공개일정));
    }

    @Test
    @DisplayName("자리를 잡은 사람에게는 보인다 — 참가해 놓고 못 찾으면 곤란하다")
    void 참가자가_보면() {
        자리잡기(비공개일정, 참가자);

        List<EventResponse> 목록 = eventService.find(null, new Viewer(참가자.getId(), false));

        assertTrue(들어있나(목록, 비공개일정));
    }

    @Test
    @DisplayName("관리자에게는 전부 보인다")
    void 관리자가_보면() {
        List<EventResponse> 목록 = eventService.find(null, new Viewer(남.getId(), true));

        assertTrue(들어있나(목록, 비공개일정));
    }

    @Test
    @DisplayName("곡 목록에도 비공개 일정의 곡은 끼지 않는다")
    void 곡목록에서도_걸러진다() {
        SongResponse 비공개곡 = 곡만들기("숨은 곡");
        songService.assignToEvent(비공개곡.id(), 비공개일정.getId());

        SongResponse 공개곡 = 곡만들기("보이는 곡");
        songService.assignToEvent(공개곡.id(), 공개일정.getId());

        List<SongResponse> 목록 = songService.search(
                new SongSearchCondition(null, null, null, null), Viewer.anonymous());

        assertTrue(곡이들어있나(목록, 공개곡));
        assertFalse(곡이들어있나(목록, 비공개곡),
                "일정 목록만 거르고 곡 목록을 두면 eventId 가 그대로 새어 나간다");
    }

    @Test
    @DisplayName("아직 일정에 안 들어간 후보곡은 누구에게나 보인다")
    void 후보곡은_남는다() {
        SongResponse 후보 = 곡만들기("아직 미정");

        List<SongResponse> 목록 = songService.search(
                new SongSearchCondition(null, null, null, null), Viewer.anonymous());

        assertTrue(곡이들어있나(목록, 후보),
                "event 가 null 인 곡까지 사라지면 조인을 잘못 건 것이다");
    }

    @Test
    @DisplayName("eventId 로 콕 집어 물어봐도 비공개 일정의 곡은 안 준다")
    void 아이디로_직접_물어봐도() {
        SongResponse 비공개곡 = 곡만들기("숨은 곡");
        songService.assignToEvent(비공개곡.id(), 비공개일정.getId());

        List<SongResponse> 목록 = songService.search(
                new SongSearchCondition(null, null, null, 비공개일정.getId()), Viewer.anonymous());

        assertTrue(목록.isEmpty());
    }

    private Event 일정만들기(String 제목, Event.Visibility 공개여부) {
        Event event = new Event(제목, LocalDate.now().plusDays(30), LocalDate.now().plusDays(7), "홍대", 주최자);
        event.setVisibility(공개여부);
        return eventRepository.save(event);
    }

    private SongResponse 곡만들기(String 제목) {
        return songService.create(
                new SongRequest(제목, "아티스트", null, null, List.of("보컬", "드럼")),
                주최자.getId()
        );
    }

    private void 자리잡기(Event event, Member member) {
        SongResponse song = 곡만들기("참가 확인용");
        songService.assignToEvent(song.id(), event.getId());
        songService.claimSlot(song.id(), song.slots().get(0).id(), member.getId(), false);
    }

    private boolean 들어있나(List<EventResponse> 목록, Event event) {
        return 목록.stream().anyMatch(e -> e.id().equals(event.getId()));
    }

    private boolean 곡이들어있나(List<SongResponse> 목록, SongResponse song) {
        return 목록.stream().anyMatch(s -> s.id().equals(song.id()));
    }
}
