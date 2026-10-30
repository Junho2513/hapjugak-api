package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Song;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
}