package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SlotRepository;
import jakarta.persistence.EntityManager;
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
 * 탈퇴.
 *
 * <p>요구는 두 가지였고, 그냥 보면 서로 어긋난다.
 * 하나는 이 사람이 남긴 것들이 「탈퇴한 사용자」로 보여야 한다는 것,
 * 다른 하나는 이 사람의 정보가 DB 에서 정말 사라져야 한다는 것이다.
 *
 * <p>행을 지우면 둘 다 안 된다. member.id 를 가리키는 외래키가 네 군데라
 * 지우려 하면 DB 가 거부하고(지금), CASCADE 를 걸면 남의 합주와 남의 곡 기록까지
 * 같이 사라진다. 그래서 행은 남기고 그 안의 사람을 비운다.
 *
 * <p>여기서 확인하는 것은 「무엇이 남고 무엇이 사라지는가」다.
 */
@SpringBootTest
@Transactional
class MemberWithdrawTest {

    @Autowired private MemberService memberService;
    @Autowired private SongService songService;
    @Autowired private EventService eventService;
    @Autowired private ActivityLogService activityLogService;

    @Autowired private MemberRepository memberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private SlotRepository slotRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private EntityManager em;

    private Member 나가는사람;
    private Member 남는사람;
    private Event 일정;

    @BeforeEach
    void 준비() {
        나가는사람 = memberRepository.save(
                new Member("gone@test.com", "{noop}pw", "나가는사람", "서울", "소개글입니다"));
        남는사람 = memberRepository.save(
                new Member("stay@test.com", "{noop}pw", "남는사람", "서울", ""));
        일정 = eventRepository.save(new Event(
                "합주 한 판", LocalDate.now().plusDays(30), LocalDate.now().plusDays(7), "홍대", 남는사람));
    }

    // ── 사라지는 것 ──────────────────────────────────────────────────

    @Test
    @DisplayName("탈퇴하면 이메일·비밀번호·지역·소개가 DB 에서 비워진다")
    void 개인정보가_사라진다() {
        Long id = 나가는사람.getId();

        memberService.withdraw(id, id);
        em.flush();
        em.clear();

        Member 남은행 = memberRepository.findById(id).orElseThrow();
        assertNull(남은행.getEmail(), "이메일이 남으면 안 된다");
        assertNull(남은행.getPassword());
        assertNull(남은행.getArea());
        assertNull(남은행.getBio());
        assertNull(남은행.getPhotoUrl());
        assertNull(남은행.getProviderId(), "구글 식별자도 사람을 가리킨다");
        assertTrue(남은행.getInstruments().isEmpty());
    }

    @Test
    @DisplayName("이름은 「탈퇴한 사용자」가 되고, 행 자체는 남는다")
    void 행은_남는다() {
        Long id = 나가는사람.getId();

        memberService.withdraw(id, id);
        em.flush();
        em.clear();

        Member 남은행 = memberRepository.findById(id).orElseThrow();
        assertEquals(Member.WITHDRAWN_NICKNAME, 남은행.getNickname());
        assertTrue(남은행.isWithdrawn());
        assertNotNull(남은행.getDeletedAt());
    }

    @Test
    @DisplayName("관리자가 탈퇴해도 관리자 자리는 따라가지 않는다")
    void 역할이_되돌아간다() {
        Member 관리자 = memberRepository.save(
                new Member("admin@test.com", "{noop}pw", "관리자", "서울", ""));
        promoteToAdmin(관리자);
        Long id = 관리자.getId();

        memberService.withdraw(id, id);
        em.flush();
        em.clear();

        assertEquals(Role.USER, memberRepository.findById(id).orElseThrow().getRole());
    }

    // ── 다시 가입 ────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 이메일로 다시 가입할 수 있고, 옛 계정과 이어지지 않는다")
    void 재가입() {
        Long 옛id = 나가는사람.getId();
        memberService.withdraw(옛id, 옛id);
        em.flush();
        em.clear();

        Long 새id = memberService.create(new MemberRequest(
                "gone@test.com", "newpw1234", "다시온사람", "부산", null, null, null)).id();

        assertNotEquals(옛id, 새id, "옛 행을 되살리면 안 된다 — 새 사람이다");
        assertEquals("다시온사람", memberRepository.findById(새id).orElseThrow().getNickname());
    }

    @Test
    @DisplayName("탈퇴한 사람은 회원 목록에 나오지 않는다")
    void 목록에서_빠진다() {
        int 전 = memberService.findAll().size();

        memberService.withdraw(나가는사람.getId(), 나가는사람.getId());
        em.flush();
        em.clear();

        assertEquals(전 - 1, memberService.findAll().size());
    }

    // ── 남기는 것 ────────────────────────────────────────────────────

    @Test
    @DisplayName("올렸던 곡과 남의 합주는 그대로 남는다")
    void 남긴_것은_남는다() {
        SongResponse 곡 = songService.createIn(
                일정.getId(), 요청("두고 가는 곡", List.of("보컬", "드럼")), 나가는사람.getId());

        memberService.withdraw(나가는사람.getId(), 나가는사람.getId());
        em.flush();
        em.clear();

        List<SongResponse> 남은곡 = songService.findByEvent(일정.getId(), null, Viewer.anonymous());
        assertEquals(1, 남은곡.size(), "올린 사람이 나갔다고 곡까지 사라지면 안 된다");
        assertEquals(곡.id(), 남은곡.get(0).id());
        assertTrue(eventRepository.findById(일정.getId()).isPresent());
    }

    @Test
    @DisplayName("활동 기록은 남고, 거기 적힌 이름만 지워진다")
    void 기록의_이름만_지운다() {
        SongResponse 곡 = songService.createIn(
                일정.getId(), 요청("기록이 남는 곡", List.of("보컬")), 나가는사람.getId());
        activityLogService.create(곡.id(),
                new ActivityLogRequest("SLOT_CLAIMED", "보컬", "보컬 자리를 잡았습니다", null),
                나가는사람.getId());

        memberService.withdraw(나가는사람.getId(), 나가는사람.getId());
        em.flush();
        em.clear();

        var 기록 = activityLogRepository.findTop200BySongIdOrderByCreatedAtDesc(곡.id());
        assertFalse(기록.isEmpty(), "기록까지 지우면 남의 곡 역사가 사라진다");
        assertTrue(기록.stream().allMatch(l -> Member.WITHDRAWN_NICKNAME.equals(l.getActorName())),
                "기록에 남은 이름 사본도 지워야 한다");
    }

    @Test
    @DisplayName("「○○ 님에게서 넘겨받았습니다」의 상대 이름도 지워진다")
    void 기록의_상대_이름도_지운다() {
        SongResponse 곡 = songService.createIn(
                일정.getId(), 요청("넘겨받은 곡", List.of("보컬")), 남는사람.getId());
        activityLogService.create(곡.id(),
                new ActivityLogRequest("SLOT_TAKEN_OVER", "보컬", null, 나가는사람.getNickname()),
                남는사람.getId());

        memberService.withdraw(나가는사람.getId(), 나가는사람.getId());
        em.flush();
        em.clear();

        var 기록 = activityLogRepository.findTop200BySongIdOrderByCreatedAtDesc(곡.id());
        assertTrue(기록.stream().noneMatch(l -> "나가는사람".equals(l.getTargetName())),
                "상대 칸에 이름이 남아 있으면 지운 것이 아니다");
    }

    // ── 자리 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("잡고 있던 자리가 비고, 성립됐던 곡은 성립이 풀린다")
    void 자리가_비워진다() {
        SongResponse 곡 = songService.createIn(
                일정.getId(), 요청("두 자리 곡", List.of("보컬", "드럼")), 남는사람.getId());
        Long 보컬 = 슬롯(곡, "보컬");
        Long 드럼 = 슬롯(곡, "드럼");
        songService.claimSlot(곡.id(), 보컬, 남는사람.getId(), false);
        SongResponse 성립 = songService.claimSlot(곡.id(), 드럼, 나가는사람.getId(), false);
        assertNotNull(성립.completedAt(), "준비가 틀렸다 — 먼저 성립시켜 두어야 한다");

        memberService.withdraw(나가는사람.getId(), 나가는사람.getId());
        em.flush();
        em.clear();

        SongResponse 뒤 = songService.findByEvent(일정.getId(), null, Viewer.anonymous()).get(0);
        assertEquals(1, 뒤.filledCount(), "나간 사람이 잡고 있던 자리는 비어야 한다");
        assertNull(뒤.completedAt(), "자리가 비었으면 성립도 풀려야 한다");
        assertTrue(slotRepository.findByMemberId(나가는사람.getId()).isEmpty());
    }

    // ── 누가 부를 수 있는가 ──────────────────────────────────────────

    @Test
    @DisplayName("남이 남을 탈퇴시킬 수는 없다")
    void 남은_못_내보낸다() {
        assertThrows(ForbiddenException.class,
                () -> memberService.withdraw(나가는사람.getId(), 남는사람.getId()));
    }

    @Test
    @DisplayName("관리자는 내보낼 수 있다")
    void 관리자는_내보낸다() {
        Member 관리자 = memberRepository.save(
                new Member("admin2@test.com", "{noop}pw", "관리자", "서울", ""));
        promoteToAdmin(관리자);

        memberService.withdraw(나가는사람.getId(), 관리자.getId());
        em.flush();
        em.clear();

        assertTrue(memberRepository.findById(나가는사람.getId()).orElseThrow().isWithdrawn());
    }

    @Test
    @DisplayName("두 번 탈퇴할 수는 없다")
    void 두_번은_안_된다() {
        Long id = 나가는사람.getId();
        memberService.withdraw(id, id);
        em.flush();

        assertThrows(ConflictException.class, () -> memberService.withdraw(id, id));
    }

    // ── 거들기 ──────────────────────────────────────────────────────

    /**
     * 역할을 바꾸는 길이 코드에 없다. 관리자는 DB 에서 직접 올린다.
     * 테스트에서만 필요한 일을 위해 운영 코드에 setter 를 뚫지 않는다.
     */
    private void promoteToAdmin(Member member) {
        em.flush();   // 아직 INSERT 가 안 나갔을 수 있다. 네이티브 UPDATE 는 그걸 기다려주지 않는다
        em.createNativeQuery("update member set role = 'ADMIN' where id = ?1")
                .setParameter(1, member.getId())
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private SongRequest 요청(String 제목, List<String> 파트) {
        return new SongRequest(제목, "아티스트", null, null, 파트);
    }

    private Long 슬롯(SongResponse 곡, String 이름) {
        return 곡.slots().stream()
                .filter(s -> 이름.equals(s.label()))
                .findFirst().orElseThrow().id();
    }
}
