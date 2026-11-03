package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;

import java.util.List;

public interface EventRepositoryCustom {

    /**
     * 보는 사람에게 보여도 되는 일정만.
     *
     * completed 가 null 이면 전체, true 면 마감된 것, false 면 진행 중인 것.
     */
    List<Event> findVisible(Boolean completed, Viewer viewer);
}
