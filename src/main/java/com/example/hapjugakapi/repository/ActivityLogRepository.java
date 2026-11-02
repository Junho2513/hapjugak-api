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
}
