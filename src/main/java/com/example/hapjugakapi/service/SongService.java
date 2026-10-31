package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.Slot;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.SongSearchCondition;
import com.example.hapjugakapi.exception.ConflictException;
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
public class SongService {

    private final SongRepository songRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;

    public SongService(SongRepository songRepository,
                       MemberRepository memberRepository,
                       EventRepository eventRepository) {
        this.songRepository = songRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public SongResponse create(SongRequest request, Long proposerId) {
        Member proposer = getMember(proposerId);

        Song song = new Song(
                request.title(),
                request.artist(),
                request.youtubeUrl(),
                request.description(),
                proposer
        );
        if (request.parts() != null) {
            for (Part part : request.parts()) {
                song.addSlot(part);
            }
        }
        return SongResponse.from(songRepository.save(song));
    }

    @Transactional
    public SongResponse update(Long songId, SongRequest request, Long memberId) {
        Song song = getSong(songId);
        checkOpen(song);
        checkProposerOrAdmin(song, memberId);

        if (request.title() != null) song.setTitle(request.title());
        if (request.artist() != null) song.setArtist(request.artist());
        if (request.youtubeUrl() != null) song.setYoutubeUrl(request.youtubeUrl());
        if (request.description() != null) song.setDescription(request.description());
        song.syncParts(request.parts());
        return SongResponse.from(song);
    }

    public List<SongResponse> findCandidates() {
        return songRepository.findByEventIsNull().stream()
                .map(SongResponse::from)
                .toList();
    }

    public List<SongResponse> findByEvent(Long eventId) {
        return songRepository.findByEventId(eventId).stream()
                .map(SongResponse::from)
                .toList();
    }

    public List<SongResponse> findAll() {
        return songRepository.findAll().stream()
                .map(SongResponse::from)
                .toList();
    }

    public List<SongResponse> search(SongSearchCondition condition) {
        return songRepository.search(condition).stream()
                .map(SongResponse::from)
                .toList();
    }

    public SongResponse findById(Long id) {
        return SongResponse.from(getSong(id));
    }

    @Transactional
    public SongResponse assignToEvent(Long songId, Long eventId) {
        Song song = getSong(songId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("합주 일정을 찾을 수 없습니다."));

        if (event.isDeadlinePassed()) {
            throw new ConflictException("곡 신청이 마감된 일정입니다.");
        }
        if (event.isCompleted()) {
            throw new ConflictException("이미 끝난 합주입니다.");
        }
        song.assignTo(event);
        return SongResponse.from(song);
    }

    @Transactional
    public SongResponse unassign(Long songId) {
        Song song = getSong(songId);
        Event event = song.getEvent();
        if (event != null && event.isCompleted()) {
            throw new ConflictException("이미 끝난 합주의 곡은 뺄 수 없습니다.");
        }
        song.unassign();
        return SongResponse.from(song);
    }

    @Transactional
    public SongResponse claimSlot(Long songId, Long slotId, Long memberId, boolean openToHandover) {
        Song song = getSong(songId);
        checkOpen(song);

        Slot slot = findSlot(song, slotId);
        slot.claimBy(getMember(memberId), openToHandover);
        return SongResponse.from(song);
    }

    /** 양도 가능으로 표시된 자리를 넘겨받는다. */
    @Transactional
    public SongResponse takeOverSlot(Long songId, Long slotId, Long memberId) {
        Song song = getSong(songId);
        checkOpen(song);

        Slot slot = findSlot(song, slotId);
        if (slot.isTaken() && slot.getMember().getId().equals(memberId)) {
            throw new ConflictException("이미 본인이 잡은 자리입니다.");
        }
        slot.takeOverBy(getMember(memberId));
        return SongResponse.from(song);
    }

    /** 본인 자리의 양도 가능 표시를 켜고 끈다. */
    @Transactional
    public SongResponse setHandover(Long songId, Long slotId, Long memberId, boolean open) {
        Song song = getSong(songId);
        checkOpen(song);

        Slot slot = findSlot(song, slotId);
        if (!slot.isTaken() || !slot.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인이 잡은 자리만 바꿀 수 있습니다.");
        }
        slot.setOpenToHandover(open);
        return SongResponse.from(song);
    }

    /** 관리자 또는 주최자가 자리를 지정하거나 비운다. */
    @Transactional
    public SongResponse assignSlot(Long songId, Long slotId,
                                   Long targetMemberId, boolean openToHandover, Long actorId) {
        Song song = getSong(songId);
        checkManager(song, actorId);

        Slot slot = findSlot(song, slotId);
        slot.assignTo(targetMemberId == null ? null : getMember(targetMemberId), openToHandover);
        return SongResponse.from(song);
    }

    @Transactional
    public SongResponse releaseSlot(Long songId, Long slotId, Long memberId) {
        Song song = getSong(songId);
        checkOpen(song);

        Slot slot = findSlot(song, slotId);
        if (!slot.isTaken()) {
            throw new ConflictException("비어 있는 자리입니다.");
        }
        if (!slot.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인이 잡은 자리만 뺄 수 있습니다.");
        }
        slot.release();
        return SongResponse.from(song);
    }

    @Transactional
    public void delete(Long songId, Long memberId) {
        Song song = getSong(songId);
        checkProposerOrAdmin(song, memberId);
        songRepository.delete(song);
    }

    private void checkOpen(Song song) {
        Event event = song.getEvent();
        if (event == null) {
            return;
        }
        if (event.isCompleted()) {
            throw new ConflictException("이미 끝난 합주입니다.");
        }
        if (event.isDeadlinePassed()) {
            throw new ConflictException("마감된 일정입니다.");
        }
    }

    private void checkProposerOrAdmin(Song song, Long memberId) {
        Member member = getMember(memberId);
        if (member.getRole() == Role.ADMIN) return;
        if (!song.isProposedBy(member)) {
            throw new ForbiddenException("곡을 올린 사람만 바꿀 수 있습니다.");
        }
    }

    /** 관리자이거나, 그 곡이 속한 합주의 주최자여야 한다. */
    private void checkManager(Song song, Long memberId) {
        Member member = getMember(memberId);
        if (member.getRole() == Role.ADMIN) return;
        Event event = song.getEvent();
        if (event != null && event.isHostedBy(member)) return;
        throw new ForbiddenException("관리자 또는 주최자만 자리를 지정할 수 있습니다.");
    }

    private Slot findSlot(Song song, Long slotId) {
        return song.getSlots().stream()
                .filter(s -> s.getId().equals(slotId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("해당 곡에 그런 자리가 없습니다."));
    }

    private Song getSong(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("곡을 찾을 수 없습니다."));
    }

    private Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
    }
}