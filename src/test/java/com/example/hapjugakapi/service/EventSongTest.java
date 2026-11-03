package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 곡이 일정 안으로 들어온 뒤의 규칙들.
 *
 * 곡이 일정 밖에 떠 있던 시절에는 물을 수 없던 것들이다.
 * "이 사람이 이 합주에 몇 곡 올렸나"를 셀 대상이 없었기 때문이다.
 */
@SpringBootTest
@Transactional
class EventSongTest {

    @Autowired private SongService songService;
    @Autowired private EventService eventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private ActivityLogService activityLogService;

    private Member 주최자;
    private Member 참가자;
    private Event 일정;

    @BeforeEach
    void 준비() {
        주최자 = memberRepository.save(new Member("host@test.com", "pw", "주최자", "서울", ""));
        참가자 = memberRepository.save(new Member("player@test.com", "pw", "참가자", "서울", ""));
        일정 = 일정만들기(LocalDate.now().plusDays(7));
    }

    @Test
    @DisplayName("일정 안에 곡을 올리면 그 일정의 곡이 된다")
    void 올리기() {
        SongResponse 곡 = songService.createIn(일정.getId(), 요청("첫 곡", List.of(Part.VOCAL, Part.DRUM)), 참가자.getId());

        assertEquals(일정.getId(), 곡.eventId());
        assertEquals(2, 곡.slots().size());
        assertEquals(1, songService.findByEvent(일정.getId()).size());
    }

    @Test
    @DisplayName("파트를 비워 보내면 그 합주의 기본 편성을 쓴다")
    void 기본_편성() {
        일정.replaceDefaultParts(List.of(Part.VOCAL, Part.GUITAR1, Part.BASS, Part.DRUM));
        eventRepository.save(일정);

        SongResponse 곡 = songService.createIn(일정.getId(), 요청("편성 없이", null), 참가자.getId());

        assertEquals(4, 곡.slots().size());
    }

    @Test
    @DisplayName("1인당 최대 곡수를 넘기면 막는다")
    void 곡수_제한() {
        일정.setMaxSongsPerUser(2);
        eventRepository.save(일정);

        songService.createIn(일정.getId(), 요청("한 곡", List.of(Part.VOCAL)), 참가자.getId());
        songService.createIn(일정.getId(), 요청("두 곡", List.of(Part.VOCAL)), 참가자.getId());

        ConflictException e = assertThrows(ConflictException.class,
                () -> songService.createIn(일정.getId(), 요청("세 곡", List.of(Part.VOCAL)), 참가자.getId()));
        assertTrue(e.getMessage().contains("2곡까지"));
    }

    @Test
    @DisplayName("제한은 사람마다 따로 센다")
    void 곡수_제한은_사람별() {
        일정.setMaxSongsPerUser(1);
        eventRepository.save(일정);

        songService.createIn(일정.getId(), 요청("참가자 곡", List.of(Part.VOCAL)), 참가자.getId());

        // 다른 사람은 아직 안 올렸으므로 통과해야 한다
        assertEquals(일정.getId(),
                songService.createIn(일정.getId(), 요청("주최자 곡", List.of(Part.VOCAL)), 주최자.getId()).eventId());
    }

    @Test
    @DisplayName("곡 신청이 마감된 일정에는 올릴 수 없다")
    void 마감된_일정() {
        Event 마감됨 = 일정만들기(LocalDate.now().minusDays(1));

        assertThrows(ConflictException.class,
                () -> songService.createIn(마감됨.getId(), 요청("늦었다", List.of(Part.VOCAL)), 참가자.getId()));
    }

    @Test
    @DisplayName("없는 일정에 올리면 404")
    void 없는_일정() {
        assertThrows(NotFoundException.class,
                () -> songService.createIn(999_999L, 요청("허공에", List.of(Part.VOCAL)), 참가자.getId()));
    }

    @Test
    @DisplayName("일정을 지우면 그 안의 곡도 함께 사라진다")
    void 일정_삭제() {
        SongResponse 곡 = songService.createIn(일정.getId(), 요청("같이 사라질 곡", List.of(Part.VOCAL)), 참가자.getId());
        songService.claimSlot(곡.id(), 곡.slots().get(0).id(), 참가자.getId(), false);

        // 활동 기록을 일부러 하나 남긴다.
        // activity_log.song_id 가 NOT NULL 이라, 이게 남아 있으면 곡 삭제를 DB 가 거부한다.
        // 기록이 없으면 이 테스트는 아무것도 증명하지 못한다.
        //
        // 엔티티를 손으로 만들지 않고 서비스를 통해 남긴다.
        // 실제로 기록이 쌓이는 경로가 이쪽이고, 테스트가 그 경로를 벗어나면
        // 테스트만 통과하고 운영에서 깨지는 상황이 생긴다.
        activityLogService.create(곡.id(),
                new ActivityLogRequest("SLOT_CLAIMED", Part.VOCAL, "보컬 자리를 잡았습니다", null),
                참가자.getId());

        eventService.delete(일정.getId(), 주최자.getId());

        assertTrue(songRepository.findById(곡.id()).isEmpty(), "곡이 남아 있으면 안 된다");
        assertTrue(activityLogRepository.findTop200BySongIdOrderByCreatedAtDesc(곡.id()).isEmpty(),
                "활동 기록이 남으면 외래키가 다음 삭제를 막는다");
    }

    private Event 일정만들기(LocalDate 마감) {
        Event event = new Event("합주 한 판", LocalDate.now().plusDays(30), 마감, "홍대", 주최자);
        return eventRepository.save(event);
    }

    private SongRequest 요청(String 제목, List<Part> 파트) {
        return new SongRequest(제목, "아티스트", null, null, 파트);
    }
}
