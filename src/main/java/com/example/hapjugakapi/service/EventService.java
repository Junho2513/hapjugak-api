package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final SongRepository songRepository;
    private final StorageService storageService;

    public EventService(EventRepository eventRepository,
                        MemberRepository memberRepository,
                        SongRepository songRepository,
                        StorageService storageService) {
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.songRepository = songRepository;
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
        if (request.feeAmount() != null) event.setFeeAmount(request.feeAmount());
        if (request.feeNote() != null) event.setFeeNote(request.feeNote());
        if (request.description() != null) event.setDescription(request.description());
        if (request.afterparty() != null) event.setAfterparty(request.afterparty());
        if (request.roomCapacity() != null) event.setRoomCapacity(request.roomCapacity());
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

    /**
     * 일정 목록.
     *
     * completed 가 null 이면 전체, true 면 마감된 것, false 면 진행 중인 것.
     * 전에는 마감된 목록이 /events/completed 라는 별도 주소였는데,
     * 그것은 "events 중 id 가 completed 인 것"으로도 읽히는 주소였다.
     * 컬렉션을 거르는 일은 경로가 아니라 질의 문자열로 표현하는 게 맞다.
     *
     * 비공개 일정은 보는 사람에 따라 빠진다. 규칙은 EventRepositoryImpl 에 적어 뒀다.
     */
    public List<EventResponse> find(Boolean completed, Viewer viewer) {
        return eventRepository.findVisible(completed, viewer).stream()
                .map(EventResponse::from)
                .toList();
    }

    /**
     * 일정 하나.
     *
     * 비공개여도 막지 않는다. 「비공개 — 링크로만 참가」는 목록에 안 뜬다는 뜻이지
     * 링크를 받은 사람도 못 본다는 뜻이 아니다. 로그인 전에 내용을 보고
     * 들어올지 정할 수 있어야 초대가 성립한다.
     */
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

        // 이 일정에 들어가 있던 곡들을 먼저 후보로 되돌린다.
        //
        // song.event_id 가 이 일정을 참조하므로 그냥 두면 DB 가 삭제를 거부한다.
        // 곡까지 지우는 선택지도 있었지만, 곡 추천은 일정보다 오래 산다.
        // 일정이 사라졌다고 "이 곡 하고 싶다"는 기록까지 없앨 이유가 없다.
        songRepository.findByEventId(id).forEach(Song::unassign);

        String cover = event.getCoverUrl();
        eventRepository.delete(event);

        // S3 삭제는 DB 가 커밋된 뒤에 해야 한다.
        // 먼저 지우면 트랜잭션이 되돌아갔을 때 커버만 사라진다.
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