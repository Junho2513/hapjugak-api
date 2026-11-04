package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop200BySongIdOrderByCreatedAtDesc(Long songId);

    /**
     * 곡 하나에 달린 로그를 한 번에 지운다.
     *
     * 곡을 삭제하려면 이걸 먼저 불러야 한다.
     * activity_log.song_id 가 song.id 를 참조하므로, 로그가 남아 있으면
     * DB 가 곡 삭제를 거부한다.
     *
     * 엔티티를 하나씩 불러와 지우는 대신 DELETE 문 하나로 끝낸다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ActivityLog l where l.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);

    /**
     * 일정을 지울 때 쓴다. 그 일정의 곡이 여러 개이므로 한 번에 지운다.
     * 곡마다 deleteBySongId 를 부르면 곡 수만큼 DELETE 가 나간다.
     *
     * 빈 목록을 넘기면 `in ()` 이 되어 문법 오류가 나므로 부르는 쪽에서 막는다.
     *
     * clearAutomatically 가 필요한 이유 —
     * 이런 벌크 DELETE 는 영속성 컨텍스트를 지나쳐 DB 만 건드린다.
     * 그래서 이미 읽어둔 ActivityLog 가 메모리에는 그대로 남고, 지워진 곡을
     * 여전히 가리킨다. 그 상태로 곡을 지우면 flush 때 Hibernate 가
     * "없는 것을 참조한다"며 거부한다(TransientPropertyValueException).
     *
     * 비운 뒤에는 들고 있던 엔티티가 전부 준영속이 되므로, 부르는 쪽은
     * 필요한 것을 다시 읽어야 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ActivityLog l where l.song.id in :songIds")
    void deleteBySongIdIn(@Param("songIds") List<Long> songIds);

    /**
     * 탈퇴한 사람의 이름을 기록에서 지운다.
     *
     * activity_log 는 actor_id(FK) 와 actor_name(이름 사본)을 함께 들고 있다.
     * 일부러 중복시킨 것이다 — 기록은 "그때 그 이름"을 보여줘야 하므로
     * 나중에 닉네임을 바꿔도 옛 기록은 옛 이름으로 남는다.
     *
     * 그 덕에 탈퇴할 때 문제가 된다. member 행에서 이름을 지워도
     * 이 사본에는 그대로 남는다. 기록을 통째로 지우면 남의 곡 역사가 사라지므로,
     * 이름만 「탈퇴한 사용자」로 덮는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivityLog l set l.actorName = :placeholder where l.actor.id = :memberId")
    int anonymizeActorName(@Param("memberId") Long memberId,
                           @Param("placeholder") String placeholder);

    /**
     * 「민수 님에게서 넘겨받았습니다」 같은 줄의 상대 이름도 지운다.
     *
     * target_name 에는 FK 가 없다. 이름 문자열만 들어 있어서 누구인지 특정할 수 없고,
     * 닉네임이 같은 사람을 가려낼 방법도 없다.
     *
     * 그래서 이름이 정확히 같은 줄을 전부 덮는다. 같은 닉네임을 쓰는 다른 사람이
     * 있었다면 그 사람 이름도 「탈퇴한 사용자」로 보이게 된다. 기록 한 줄의
     * 표시가 뭉개지는 쪽이, 나간 사람의 이름이 남아 있는 쪽보다 낫다고 봤다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivityLog l set l.targetName = :placeholder where l.targetName = :nickname")
    int anonymizeTargetName(@Param("nickname") String nickname,
                            @Param("placeholder") String placeholder);
}
