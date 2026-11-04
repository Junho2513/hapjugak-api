package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.PartNames;
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
 * 자리를 <b>이름</b>으로 다루게 바꾼 뒤의 규칙들.
 *
 * <p>전에는 정해진 8가지(enum) 중에서만 고를 수 있어서 두 가지를 못 했다.
 * 보컬이 둘인 곡을 적는 것과, 목록에 없는 악기를 적는 것이다. 둘 다 여기서 확인한다.
 */
@SpringBootTest
@Transactional
class PartLabelTest {

    @Autowired
    private SongService songService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    private Member 올린이;

    /** 곡은 반드시 어느 일정 안에 있다. */
    private Event 일정;

    @BeforeEach
    void setUp() {
        올린이 = memberRepository.save(new Member("p@test.com", "pw", "올린이", "서울", ""));
        일정 = eventRepository.save(new Event(
                "합주", LocalDate.now().plusDays(30), LocalDate.now().plusDays(7), "홍대", 올린이));
    }

    private SongResponse 올리기(List<String> 파트) {
        return songService.createIn(일정.getId(),
                new SongRequest("곡", "아티스트", null, null, 파트), 올린이.getId());
    }

    @Test
    @DisplayName("같은 파트를 여러 명 모집할 수 있다")
    void 보컬_둘() {
        SongResponse 곡 = 올리기(List.of("보컬1", "보컬2", "기타", "드럼"));

        assertEquals(4, 곡.totalCount());
        assertEquals(List.of("보컬1", "보컬2", "기타", "드럼"),
                곡.slots().stream().map(s -> s.label()).toList());
    }

    @Test
    @DisplayName("목록에 없는 파트도 직접 적을 수 있다")
    void 직접_적은_파트() {
        SongResponse 곡 = 올리기(List.of("보컬", "색소폰", "코러스"));

        assertEquals(3, 곡.totalCount());
        assertTrue(곡.slots().stream().anyMatch(s -> "색소폰".equals(s.label())));
    }

    @Test
    @DisplayName("어떤 차례로 적든 보컬·통기타·기타·베이스·드럼·키보드 순으로 선다")
    void 차례() {
        SongResponse 곡 = 올리기(List.of("드럼", "보컬", "베이스"));

        assertEquals(List.of("보컬", "베이스", "드럼"),
                곡.slots().stream().map(s -> s.label()).toList());
    }

    @Test
    @DisplayName("같은 파트가 여럿이면 번호 순으로 나란히 선다")
    void 차례_번호() {
        SongResponse 곡 = 올리기(List.of("키보드2","베이스","보컬2","기타2","보컬1","통기타","드럼","기타1","키보드1"));

        assertEquals(List.of("보컬1","보컬2","통기타","기타1","기타2","베이스","드럼","키보드1","키보드2"),
                곡.slots().stream().map(s -> s.label()).toList());
    }

    @Test
    @DisplayName("직접 적은 파트는 맨 뒤에, 적은 차례 그대로")
    void 차례_직접적은것() {
        SongResponse 곡 = 올리기(List.of("신디사이저","보컬","트럼본","기타1","드럼","베이스1","베이스2","키보드"));

        assertEquals(List.of("보컬","기타1","베이스1","베이스2","드럼","키보드","신디사이저","트럼본"),
                곡.slots().stream().map(s -> s.label()).toList());
    }

    @Test
    @DisplayName("같은 이름을 두 번 적으면 하나로 친다")
    void 이름_중복() {
        SongResponse 곡 = 올리기(List.of("보컬", "보컬", "드럼"));

        assertEquals(2, 곡.totalCount());
    }

    @Test
    @DisplayName("편성을 고쳐도 이름이 남은 자리의 사람은 그대로 있다")
    void 편성_수정() {
        SongResponse 곡 = 올리기(List.of("보컬", "기타", "드럼"));
        Long 보컬자리 = 곡.slots().stream()
                .filter(s -> "보컬".equals(s.label())).findFirst().orElseThrow().id();
        songService.claimSlot(곡.id(), 보컬자리, 올린이.getId(), false);

        // 「기타」를 빼고 「베이스」를 넣는다. 보컬은 그대로다.
        SongResponse 바뀜 = songService.update(곡.id(),
                new SongRequest(null, null, null, null, List.of("보컬", "베이스", "드럼")),
                올린이.getId());

        assertEquals(3, 바뀜.totalCount());
        assertEquals(1, 바뀜.filledCount(), "보컬에 들어와 있던 사람이 빠지면 안 된다");
        assertTrue(바뀜.slots().stream()
                .anyMatch(s -> "보컬".equals(s.label()) && s.taken()));
        assertFalse(바뀜.slots().stream().anyMatch(s -> "기타".equals(s.label())));
    }

    @Test
    @DisplayName("이름에서 악기 종류를 짐작한다 — 번호가 붙어도 같은 종류다")
    void 종류_짐작() {
        assertEquals(Part.VOCAL, PartNames.kindOf("보컬2"));
        assertEquals(Part.GUITAR1, PartNames.kindOf("기타1"));
        assertEquals(Part.ACOUSTIC, PartNames.kindOf("통기타"), "「기타」보다 먼저 걸려야 한다");
        assertEquals(Part.BASS, PartNames.kindOf("베이스"));
        assertEquals(Part.KEY1, PartNames.kindOf("키보드2"));
    }

    @Test
    @DisplayName("모르는 이름은 종류가 없다 — 막는 것보다 통과시키는 쪽이 낫다")
    void 모르는_이름() {
        assertNull(PartNames.kindOf("색소폰"));
        assertNull(PartNames.kindOf(""));
        assertNull(PartNames.kindOf(null));
    }

    @Test
    @DisplayName("일정의 기본 편성은 자리 이름으로 펴진다")
    void 기본_편성() {
        assertEquals(List.of("보컬", "기타1", "베이스", "드럼"),
                PartNames.labelsOf(List.of(Part.VOCAL, Part.GUITAR1, Part.BASS, Part.DRUM)));
    }
}
