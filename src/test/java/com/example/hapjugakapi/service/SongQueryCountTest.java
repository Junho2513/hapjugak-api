package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.SongSearchCondition;
import com.example.hapjugakapi.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 곡 목록을 한 번 조회할 때 SQL 이 몇 번 나가는지 센다.
 *
 * 화면을 띄워서 로그 줄 수를 세는 방법도 있지만 그건 로컬 DB 에 데이터가 있어야 하고,
 * 셀 때마다 숫자가 달라진다. 여기서는 데이터를 직접 만들고 Hibernate 의 통계로 세므로
 * 몇 번을 돌려도 같은 숫자가 나온다.
 *
 * 이 테스트는 N+1 이 다시 생기는 것을 막는 감시자다.
 * 누군가 fetchJoin 을 빼거나, 응답 DTO 가 새 연관 엔티티를 건드리기 시작하면
 * 쿼리 수가 늘어나면서 여기서 먼저 터진다.
 * 측정값이 2026-11-02 기준 곡 80건에 1개였으므로 한도를 2로 둔다.
 */
@SpringBootTest
@Transactional
class SongQueryCountTest {

    private static final int 회원수 = 10;
    private static final int 곡수 = 80;   // 운영 규모에 맞춘다

    @Autowired private SongService songService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    private static final int 허용_쿼리수 = 2;

    @Test
    @DisplayName("곡 목록을 전체 조회해도 쿼리는 몇 개 되지 않는다")
    void 목록조회_쿼리수() {
        준비한다();

        long 쿼리수 = 쿼리수를_센다(() -> songService.search(
                new SongSearchCondition(null, null, null, null)));

        System.out.println();
        System.out.println("════════ 곡 " + 곡수 + "건 조회 → 쿼리 " + 쿼리수 + "개 ════════");
        System.out.println();

        assertTrue(쿼리수 <= 허용_쿼리수,
                "N+1 이 다시 생겼습니다. 곡 " + 곡수 + "건에 쿼리가 " + 쿼리수 + "개 나갔습니다.");
    }

    /**
     * 회원 10명, 곡 20개, 곡마다 자리 4개.
     * 자리의 절반은 주인이 있어서 slot.getMember() 도 건드리게 만든다.
     */
    private void 준비한다() {
        List<Member> 회원들 = new ArrayList<>();
        for (int i = 0; i < 회원수; i++) {
            회원들.add(memberRepository.save(
                    new Member("m" + i + "@test.com", "pw", "회원" + i, "서울", "")));
        }

        List<Part> 편성 = List.of(Part.VOCAL, Part.GUITAR1, Part.BASS, Part.DRUM);

        for (int i = 0; i < 곡수; i++) {
            Member 추천자 = 회원들.get(i % 회원수);
            SongResponse 곡 = songService.create(
                    new SongRequest("곡" + i, "아티스트" + i, null, null, 편성),
                    추천자.getId());

            // 자리 네 개 중 두 개를 채운다
            for (int s = 0; s < 2; s++) {
                Member 연주자 = 회원들.get((i + s) % 회원수);
                songService.claimSlot(곡.id(), 곡.slots().get(s).id(), 연주자.getId(), false);
            }
        }
    }

    /**
     * 1차 캐시를 비우고 나서 센다.
     *
     * 비우지 않으면 앞서 만들 때 올라온 엔티티들이 그대로 남아 있어서
     * 지연 로딩이 일어나지 않는다. 실제 요청은 빈 영속성 컨텍스트에서 시작하므로
     * 그 상태를 만들어줘야 숫자가 현실과 같아진다.
     */
    private long 쿼리수를_센다(Supplier<?> 작업) {
        em.flush();
        em.clear();

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        작업.get();

        return stats.getPrepareStatementCount();
    }
}
