package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;

    public EventService(EventRepository eventRepository, MemberRepository memberRepository) {
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        Member host = memberRepository.findById(request.hostId())
                .orElseThrow(() -> new NotFoundException("주최자를 찾을 수 없습니다."));

        Event event = new Event(
                request.title(),
                request.date(),
                request.deadline(),
                request.place(),
                host
        );
        return EventResponse.from(eventRepository.save(event));
    }

    public List<EventResponse> findAll() {
        return eventRepository.findAllByOrderByDateDesc().stream()
                .map(EventResponse::from)
                .toList();
    }

    public List<EventResponse> findCompleted() {
        return eventRepository.findByCompletedTrueOrderByDateDesc().stream()
                .map(EventResponse::from)
                .toList();
    }

    public EventResponse findById(Long id) {
        return EventResponse.from(getEvent(id));
    }

    @Transactional
    public EventResponse complete(Long id) {
        Event event = getEvent(id);
        event.complete();
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(Long id) {
        eventRepository.delete(getEvent(id));
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("합주 일정을 찾을 수 없습니다."));
    }
}