package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.exception.ForbiddenException;
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
    private final StorageService storageService;

    public EventService(EventRepository eventRepository,
                        MemberRepository memberRepository,
                        StorageService storageService) {
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.storageService = storageService;
    }

    @Transactional
    public EventResponse create(EventRequest request, Long hostId) {
        Member host = memberRepository.findById(hostId)
                .orElseThrow(() -> new NotFoundException("주최자를 찾을 수 없습니다."));

        Event event = new Event(
                request.title(),
                request.date(),
                request.deadline(),
                request.studio(),
                host
        );
        apply(event, request);
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request, Long memberId) {
        Event event = getEvent(id);
        checkHostOrAdmin(event, memberId);
        if (request.title() != null) event.setTitle(request.title());
        if (request.date() != null) event.setDate(request.date());
        if (request.deadline() != null) event.setDeadline(request.deadline());
        apply(event, request);
        return EventResponse.from(event);
    }

    /** 생성·수정에 공통으로 쓰는 선택 항목 반영. null은 "바꾸지 않음". */
    private void apply(Event event, EventRequest request) {
        if (request.timeStart() != null) event.setTimeStart(request.timeStart());
        if (request.timeEnd() != null) event.setTimeEnd(request.timeEnd());
        if (request.region() != null) event.setRegion(request.region());
        if (request.studio() != null) event.setStudio(request.studio());
        if (request.room() != null) event.setRoom(request.room());
        if (request.genre() != null) event.setGenre(request.genre());
        if (request.fee() != null) event.setFee(request.fee());
        if (request.description() != null) event.setDescription(request.description());
        if (request.afterparty() != null) event.setAfterparty(request.afterparty());
        if (request.capacity() != null) event.setCapacity(request.capacity());
        if (request.maxSongsPerUser() != null) event.setMaxSongsPerUser(request.maxSongsPerUser());
        if (request.visibility() != null) event.setVisibility(request.visibility());
        applyCover(event, request.coverUrl());
        if (request.defaultParts() != null) event.replaceDefaultParts(request.defaultParts());
        if (request.completed() != null) {
            if (request.completed()) event.complete();
            else event.reopen();
        }
    }

    /**
     * null  — 커버를 건드리지 않는다
     * ""    — 커버를 지운다
     * 그 외 — 업로드된 파일을 검증하고 주소를 저장한다
     */
    private void applyCover(Event event, String requested) {
        if (requested == null) return;

        String current = event.getCoverUrl();
        if (requested.equals(current)) return;   // 안 바뀌었으면 S3를 부를 이유가 없다

        String verified = storageService.verify(requested, UploadKind.COVER);
        event.setCoverUrl(verified);
        storageService.deleteByUrl(current);     // 교체되었으니 옛 파일은 지운다
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
    public EventResponse complete(Long id, Long memberId) {
        Event event = getEvent(id);
        checkHostOrAdmin(event, memberId);
        event.complete();
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        Event event = getEvent(id);
        checkHostOrAdmin(event, memberId);
        String cover = event.getCoverUrl();
        eventRepository.delete(event);
        storageService.deleteByUrl(cover);
    }

    private void checkHostOrAdmin(Event event, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        if (member.getRole() == Role.ADMIN) return;
        if (!event.isHostedBy(member)) {
            throw new ForbiddenException("주최자만 변경할 수 있습니다.");
        }
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("합주 일정을 찾을 수 없습니다."));
    }
}