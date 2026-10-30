package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Part;
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
    public List<Song> search(SongSearchCondition condition) {
        QSong song = QSong.song;
        QSlot slot = QSlot.slot;
        QMember slotMember = new QMember("slotMember");

        return queryFactory
                .selectFrom(song).distinct()
                .leftJoin(song.proposer).fetchJoin()
                .leftJoin(song.slots, slot).fetchJoin()
                .leftJoin(slot.member, slotMember).fetchJoin()
                .where(
                        keywordContains(song, condition.keyword()),
                        candidateOnly(song, condition.candidateOnly()),
                        eventIdEq(song, condition.eventId()),
                        hasOpenPart(song, condition.openPart())
                )
                .orderBy(song.id.desc())
                .fetch();
    }

    private BooleanExpression keywordContains(QSong song, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return song.title.containsIgnoreCase(keyword)
                .or(song.artist.containsIgnoreCase(keyword));
    }

    private BooleanExpression candidateOnly(QSong song, Boolean candidateOnly) {
        if (candidateOnly == null || !candidateOnly) {
            return null;
        }
        return song.event.isNull();
    }

    private BooleanExpression eventIdEq(QSong song, Long eventId) {
        if (eventId == null) {
            return null;
        }
        return song.event.id.eq(eventId);
    }

    private BooleanExpression hasOpenPart(QSong song, Part part) {
        if (part == null) {
            return null;
        }
        QSlot openSlot = new QSlot("openSlot");
        return JPAExpressions
                .selectOne()
                .from(openSlot)
                .where(
                        openSlot.song.eq(song),
                        openSlot.part.eq(part),
                        openSlot.member.isNull()
                )
                .exists();
    }
}