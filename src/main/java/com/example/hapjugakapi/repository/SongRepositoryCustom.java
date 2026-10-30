package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.SongSearchCondition;

import java.util.List;

public interface SongRepositoryCustom {

    List<Song> search(SongSearchCondition condition);
}