package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
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

/**
 * 성립한 <b>순간</b>을 적어 두는 규칙.
 *
 * <p>화면은 성립한 곡을 이 시각 순서로 줄 세운다 — 먼저 성립한 곡이 위다.
 * 핵심은 <b>풀릴 때 지운다</b>는 것이다. 그래야 다시 성립했을 때 원래 자리로
 * 돌아가지 않고 성립 곡들의 맨 아래로 붙는다.
 *
 * <pre>
 *   버터플라이 · 브레이브하트 · 오도리코   (이 순서로 성립)
 *   → 버터플라이에서 한 명 나감
 *   → 브레이브하트 · 오도리코
 *   → 버터플라이가 다시 채워짐
 *   → 브레이브하트 · 오도리코 · 버터플라이   (맨 위가 아니라 맨 아래)
 * </pre>
 */
@SpringBootTest
@Transactional
class SongCompletionOrderTest {

    @Autowired
    private SongService songService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    private Member 보컬, 기타;

    /** 곡은 반드시 어느 일정 안에 있다. */
    private Event 일정;

    @BeforeEach
    void setUp() {
        보컬 = memberRepository.save(new Member("v@test.com", "pw", "보컬맨", "서울", ""));
        기타 = memberRepository.save(new Member("g@test.com", "pw", "기타맨", "서울", ""));
        일정 = eventRepository.save(new Event(
                "합주", LocalDate.now().plusDays(30), LocalDate.now().plusDays(7), "홍대", 보컬));
    }

    private SongResponse 두자리곡(String 제목) {
        return songService.createIn(일정.getId(),
                new SongRequest(제목, "아티스트", null, null, List.of("보컬", "기타")), 보컬.getId());
    }

    private Long 자리(SongResponse 곡, String 이름) {
        return 곡.slots().stream()
                .filter(s -> 이름.equals(s.label())).findFirst().orElseThrow().id();
    }

    /** 두 자리를 모두 채워 성립시킨다. */
    private SongResponse 성립시키기(SongResponse 곡) {
        songService.claimSlot(곡.id(), 자리(곡, "보컬"), 보컬.getId(), false);
        return songService.claimSlot(곡.id(), 자리(곡, "기타"), 기타.getId(), false);
    }

    @Test
    @DisplayName("자리가 다 차면 성립한 시각이 적힌다")
    void 성립하면_적힌다() {
        SongResponse 곡 = 두자리곡("버터플라이");
        assertNull(곡.completedAt(), "아직 비어 있는 곡에는 시각이 없다");

        SongResponse 찬곡 = 성립시키기(곡);

        assertTrue(찬곡.complete());
        assertNotNull(찬곡.completedAt());
    }

    @Test
    @DisplayName("한 자리라도 비면 성립 시각이 지워진다")
    void 풀리면_지워진다() {
        SongResponse 곡 = 성립시키기(두자리곡("버터플라이"));
        assertNotNull(곡.completedAt());

        SongResponse 풀린곡 = songService.releaseSlot(곡.id(), 자리(곡, "기타"), 기타.getId());

        assertFalse(풀린곡.complete());
        assertNull(풀린곡.completedAt(), "다시 성립할 때 맨 아래로 가려면 지워져 있어야 한다");
    }

    @Test
    @DisplayName("풀렸다 다시 성립하면 시각이 새로 찍힌다 — 원래 자리로 돌아가지 않는다")
    void 다시_성립하면_새_시각() throws Exception {
        SongResponse 버터플라이 = 성립시키기(두자리곡("버터플라이"));
        Thread.sleep(5);
        SongResponse 브레이브하트 = 성립시키기(두자리곡("브레이브하트"));

        assertTrue(버터플라이.completedAt().isBefore(브레이브하트.completedAt()),
                "먼저 성립한 곡이 앞선다");

        // 버터플라이에서 한 명 나갔다가 다시 들어온다
        songService.releaseSlot(버터플라이.id(), 자리(버터플라이, "기타"), 기타.getId());
        Thread.sleep(5);
        SongResponse 다시 = songService.claimSlot(
                버터플라이.id(), 자리(버터플라이, "기타"), 기타.getId(), false);

        assertTrue(다시.completedAt().isAfter(브레이브하트.completedAt()),
                "다시 성립한 곡은 먼저 성립해 있던 곡보다 뒤에 와야 한다");
    }

    @Test
    @DisplayName("편성을 늘려 자리가 비면 성립이 풀린다")
    void 편성을_늘리면_풀린다() {
        SongResponse 곡 = 성립시키기(두자리곡("버터플라이"));
        assertNotNull(곡.completedAt());

        SongResponse 바뀜 = songService.update(곡.id(),
                new SongRequest(null, null, null, null, List.of("보컬", "기타", "드럼")),
                보컬.getId());

        assertFalse(바뀜.complete());
        assertNull(바뀜.completedAt());
    }
}
