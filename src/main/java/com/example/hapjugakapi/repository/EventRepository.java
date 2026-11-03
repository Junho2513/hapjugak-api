package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 목록 조회는 EventRepositoryCustom(QueryDSL)으로 옮겼다.
 * 마감 여부와 공개 여부가 함께 걸리기 시작하면서
 * 메서드 이름으로 조건을 표현하는 방식으로는 감당이 안 됐다.
 */
public interface EventRepository extends JpaRepository<Event, Long>, EventRepositoryCustom {
}
