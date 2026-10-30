package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderByDateDesc();

    List<Event> findByCompletedTrueOrderByDateDesc();
}