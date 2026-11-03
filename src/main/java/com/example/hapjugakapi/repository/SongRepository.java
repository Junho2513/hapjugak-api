package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Song;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long>, SongRepositoryCustom {

    List<Song> findByEventIsNull();

    List<Song> findByEventId(Long eventId);

    /** 「1인당 최대 곡수」를 지키는지 보기 위한 것. */
    long countByEventIdAndProposerId(Long eventId, Long proposerId);
}