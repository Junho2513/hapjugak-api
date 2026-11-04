package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.QEvent;
import com.example.hapjugakapi.domain.QMember;
import com.example.hapjugakapi.domain.QSlot;
import com.example.hapjugakapi.domain.QSong;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.SongSearchCondition;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class SongRepositoryImpl implements SongRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public SongRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<Song> search(SongSearchCondition condition, Viewer viewer) {
        QSong song = QSong.song;
        QSlot slot = QSlot.slot;
        QMember slotMember = new QMember("slotMember");
        QEvent event = QEvent.event;

        return queryFactory
                .selectFrom(song).distinct()
                .leftJoin(song.proposer).fetchJoin()
                .leftJoin(song.slots, slot).fetchJoin()
                .leftJoin(slot.member, slotMember).fetchJoin()
                // 공개 여부를 보려면 일정을 붙여야 한다.
                //
                // left join 으로 둔다. 지금은 곡에 일정이 반드시 있으므로(V8) inner join
                // 이어도 결과가 같지만, song.event.visibility 처럼 바로 타고 들어가면
                // QueryDSL 이 묵시적으로 join 을 만든다. 어떤 join 이 생기는지가
                // 눈에 보이지 않는 쪽이 나중에 더 비싸다.
                .leftJoin(song.event, event)
                .where(
                        keywordContains(song, condition.keyword()),
                        eventIdEq(song, condition.eventId()),
                        hasOpenPart(song, condition.openPart()),
                        eventVisibleTo(song, event, viewer)
                )
                .orderBy(song.id.desc())
                .fetch();
    }

    /**
     * 비공개 일정에 들어간 곡은 목록에서 뺀다.
     *
     * 일정 목록만 거르고 여기를 두면 반만 막은 것이 된다.
     * GET /songs 가 eventId 를 그대로 실어 보내므로, 그것만으로
     * "비공개 일정 7번이 있다"와 그 안의 곡 목록이 드러난다.
     * 단건 조회는 열려 있으니 id 를 아는 것은 곧 링크를 받은 것과 같다.
     *
     * 규칙은 일정 목록과 같다 — 공개 일정, 내가 연 일정, 내가 자리를 잡은 일정, 관리자.
     * 아직 일정에 안 들어간 후보곡은 숨길 이유가 없으므로 항상 남는다.
     */
    private BooleanExpression eventVisibleTo(QSong song, QEvent event, Viewer viewer) {
        if (viewer.admin()) {
            return null;
        }

        // song.event_id 를 보는 것뿐이라 조인이 필요 없다.
        BooleanExpression 후보곡 = song.event.isNull();
        BooleanExpression 공개됨 = event.visibility.eq(Event.Visibility.PUBLIC);

        if (viewer.isAnonymous()) {
            return 후보곡.or(공개됨);
        }
        return 후보곡
                .or(공개됨)
                .or(event.host.id.eq(viewer.memberId()))
                .or(자리를잡았음(event, viewer.memberId()));
    }

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

    private BooleanExpression keywordContains(QSong song, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return song.title.containsIgnoreCase(keyword)
                .or(song.artist.containsIgnoreCase(keyword));
    }

    private BooleanExpression eventIdEq(QSong song, Long eventId) {
        if (eventId == null) {
            return null;
        }
        return song.event.id.eq(eventId);
    }

    /**
     * 그 이름의 자리가 비어 있는 곡을 찾는다.
     *
     * <p>앞부분만 맞아도 걸리게 한 것은 의도다. 자리 이름에는 번호가 붙는다
     * (「기타1」, 「기타2」). 「기타」로 찾는 사람은 둘 중 아무 자리나 찾는 것이다.
     */
    private BooleanExpression hasOpenPart(QSong song, String part) {
        if (part == null || part.isBlank()) {
            return null;
        }
        QSlot openSlot = new QSlot("openSlot");
        return JPAExpressions
                .selectOne()
                .from(openSlot)
                .where(
                        openSlot.song.eq(song),
                        openSlot.label.startsWith(part.trim()),
                        openSlot.member.isNull()
                )
                .exists();
    }
}
