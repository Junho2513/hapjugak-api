package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.Slot;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
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
        for (Part part : request.parts()) {
            song.addSlot(part);
        }
        return SongResponse.from(songRepository.save(song));
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
    public SongResponse claimSlot(Long songId, Long slotId, Long memberId) {
        Song song = getSong(songId);
        checkOpen(song);

        Slot slot = findSlot(song, slotId);
        slot.claimBy(getMember(memberId));
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
        if (!song.getProposer().getId().equals(memberId)) {
            throw new ForbiddenException("곡을 올린 사람만 삭제할 수 있습니다.");
        }
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