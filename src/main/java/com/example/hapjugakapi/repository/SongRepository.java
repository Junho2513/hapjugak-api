package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Song;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long>, SongRepositoryCustom {

    List<Song> findByEventIsNull();

    List<Song> findByEventId(Long eventId);
}