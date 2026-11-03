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
}
