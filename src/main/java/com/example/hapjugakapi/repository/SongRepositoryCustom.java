package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.SongSearchCondition;

import java.util.List;

public interface SongRepositoryCustom {

    /**
     * condition 은 "무엇을 찾는가", viewer 는 "누가 찾는가"다.
     * 둘을 한 record 에 섞지 않은 이유: 조건은 사용자가 고르는 것이고
     * 보는 사람은 토큰에서 나온다. 섞으면 조건으로 신분을 위조할 길이 생긴다.
     */
    List<Song> search(SongSearchCondition condition, Viewer viewer);
}