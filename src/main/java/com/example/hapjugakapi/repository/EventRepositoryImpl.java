package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.QEvent;
import com.example.hapjugakapi.domain.QSlot;
import com.example.hapjugakapi.domain.QSong;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EventRepositoryImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public EventRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /**
     * 전에는 findAll / findByCompletedTrue / findByCompletedFalse 세 개를 서비스에서 골라 썼다.
     * 거기에 공개 여부까지 더하면 경우의 수가 여섯 개가 된다.
     * 조건이 둘 이상 겹치기 시작하면 메서드 이름으로 푸는 방식은 더 이상 늘릴 수 없다.
     *
     * host 를 fetch join 하는 이유: EventResponse 가 주최자 닉네임을 쓴다.
     * 없으면 일정 수만큼 회원 조회가 더 나간다.
     */
    @Override
    public List<Event> findVisible(Boolean completed, Viewer viewer) {
        QEvent event = QEvent.event;

        return queryFactory
                .selectFrom(event)
                .leftJoin(event.host).fetchJoin()
                .where(
                        completedEq(event, completed),
                        visibleTo(event, viewer)
                )
                // 날짜 미정을 맨 위에 둔다. 아직 잡는 중이라 사람 눈이 가야 하는 쪽이다.
                // MySQL 은 DESC 정렬에서 NULL 을 뒤로 보내므로 명시해야 한다.
                .orderBy(event.date.desc().nullsFirst())
                .fetch();
    }

    private BooleanExpression completedEq(QEvent event, Boolean completed) {
        if (completed == null) {
            return null;   // 조건 없음. QueryDSL 은 null 인 조건을 그냥 건너뛴다.
        }
        return event.completed.eq(completed);
    }

    /**
     * 「비공개 — 링크로만 참가」가 실제로 뜻하는 것.
     *
     * 전에는 목록이 공개·비공개를 구분하지 않고 전부 돌려줬다.
     * 게다가 응답에 visibility 가 그대로 실려서 어느 게 비공개인지까지 알려줬다.
     * 화면에서 비공개를 고른 사람은 안 보인다고 믿고 있었으므로,
     * 기능이 없는 것보다 나빴다. 설정이 거짓말을 하고 있었다.
     *
     * 목록에 넣어주는 경우
     * - 공개 일정
     * - 내가 연 일정
     * - 내가 이미 자리를 잡은 일정 (참가한 사람은 당연히 자기 합주를 찾을 수 있어야 한다)
     * - 관리자
     *
     * 단건 조회(GET /events/{id})는 일부러 열어둔다. 그게 "링크로만 참가"의 뜻이다.
     * 링크를 받은 사람은 로그인 전에도 내용을 보고 들어올지 정할 수 있어야 한다.
     */
    private BooleanExpression visibleTo(QEvent event, Viewer viewer) {
        if (viewer.admin()) {
            return null;   // 관리자는 전부 본다
        }

        BooleanExpression 공개됨 = event.visibility.eq(Event.Visibility.PUBLIC);

        if (viewer.isAnonymous()) {
            return 공개됨;
        }
        return 공개됨
                .or(event.host.id.eq(viewer.memberId()))
                .or(자리를잡았음(event, viewer.memberId()));
    }

    /** 이 일정의 어느 곡에든 내 이름이 적힌 자리가 있는가. */
    private BooleanExpression 자리를잡았음(QEvent event, Long memberId) {
        QSlot slot = new QSlot("joinedSlot");
        QSong song = new QSong("joinedSong");

        return JPAExpressions
                .selectOne()
                .from(slot)
                .join(slot.song, song)
                .where(
                        song.event.eq(event),
                        slot.member.id.eq(memberId)
                )
                .exists();
    }
}
