package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.Viewer;
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
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SongService {

    private final SongRepository songRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final ActivityLogRepository activityLogRepository;

    public SongService(SongRepository songRepository,
                       MemberRepository memberRepository,
                       EventRepository eventRepository,
                       ActivityLogRepository activityLogRepository) {
        this.songRepository = songRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * 일정 없이 곡을 만든다. 없어질 예정이다.
     *
     * 화면이 아직 이 경로로 곡을 올린다. 지금 지우면 배포하는 순간 곡 올리기가 죽는다.
     * 새 화면이 /events/{id}/songs 로 올리게 바뀐 뒤, song.event_id 를 NOT NULL 로
     * 조이는 V5 와 함께 지운다.
     */
    @Deprecated(forRemoval = true)
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

    /**
     * 일정 안에 곡을 올린다.
     *
     * 전에는 곡을 먼저 만들어 두고 나중에 일정에 끼워 넣었다.
     * 그래서 "어느 합주에서 할지 모르는 곡"이라는 상태가 존재했고,
     * 화면에는 그 곡들만 모아 보는 탭이 따로 있었다.
     *
     * 이 사이트는 오프라인 합주를 돕는 곳이다.
     * 곡은 "언젠가 하고 싶은 것"이 아니라 "이 날 이 합주실에서 할 것"이다.
     * 그래서 곡은 처음부터 일정 안에서 만들어진다.
     */
    @Transactional
    public SongResponse createIn(Long eventId, SongRequest request, Long proposerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("합주 일정을 찾을 수 없습니다."));
        checkAcceptingSongs(event);
        checkSongQuota(event, proposerId);

        Member proposer = getMember(proposerId);

        Song song = new Song(
                request.title(),
                request.artist(),
                request.youtubeUrl(),
                request.description(),
                proposer
        );
        song.assignTo(event);

        // 파트를 안 적어 보내면 그 합주의 기본 편성을 그대로 쓴다.
        // 매번 같은 파트를 다시 고르게 하는 것은 일을 두 번 시키는 것이다.
        Collection<Part> parts = (request.parts() == null || request.parts().isEmpty())
                ? event.getDefaultParts()
                : request.parts();
        for (Part part : parts) {
            song.addSlot(part);
        }
        return SongResponse.from(songRepository.save(song));
    }

    /** 그 일정의 곡. 일정 자체가 링크로 열려 있으므로 곡도 같은 규칙을 따른다. */
    public List<SongResponse> findByEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("합주 일정을 찾을 수 없습니다.");
        }
        return songRepository.search(new SongSearchCondition(null, null, null, eventId), Viewer.anonymous())
                .stream()
                .map(SongResponse::from)
                .toList();
    }

    /**
     * 「1인당 최대 곡수」가 드디어 무언가를 막는다.
     *
     * 전에는 일정에 적어두기만 하고 아무 데서도 보지 않았다.
     * 곡이 일정 밖에 떠 있던 구조에서는 셀 수가 없었기 때문이다 —
     * "이 사람이 이 합주에 몇 곡 올렸나"를 물을 대상이 없었다.
     * 곡이 일정 안으로 들어오면서 비로소 셀 수 있게 됐다.
     */
    private void checkSongQuota(Event event, Long proposerId) {
        int limit = event.getMaxSongsPerUser();
        if (limit <= 0) {
            return;   // 0 이면 제한 없음
        }
        long mine = songRepository.countByEventIdAndProposerId(event.getId(), proposerId);
        if (mine >= limit) {
            throw new ConflictException("이 합주에는 한 사람당 " + limit + "곡까지 올릴 수 있습니다.");
        }
    }

    private void checkAcceptingSongs(Event event) {
        if (event.isCompleted()) {
            throw new ConflictException("이미 끝난 합주입니다.");
        }
        if (event.isDeadlinePassed()) {
            throw new ConflictException("곡 신청이 마감된 일정입니다.");
        }
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

    /**
     * 곡 목록 조회의 유일한 입구.
     *
     * 예전에는 findAll / findCandidates / findByEvent / search 네 개가 따로 있었다.
     * 그런데 뒤 세 개는 search 의 조건 하나씩을 고정한 것에 지나지 않았고,
     * 무엇보다 fetchJoin 이 들어 있는 건 search 뿐이었다.
     * 화면이 부르던 findAll 은 연관 엔티티를 지연 로딩하느라
     * 곡 80건에 쿼리 100개를 쓰고 있었다.
     *
     * 조건이 전부 null 이면 전체 조회가 되므로 search 하나로 충분하다.
     *
     * 비공개 일정에 들어간 곡은 보는 사람에 따라 빠진다. 규칙은 SongRepositoryImpl 에 적어 뒀다.
     */
    public List<SongResponse> search(SongSearchCondition condition, Viewer viewer) {
        return songRepository.search(condition, viewer).stream()
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

        checkAcceptingSongs(event);
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

        // 활동 로그를 먼저 지운다.
        // activity_log.song_id 가 이 곡을 참조하고 있어서,
        // 로그가 남은 채로 곡을 지우면 DB 가 거부한다.
        //
        // 로그를 남겨두는 선택지도 있었지만 로그는 곡별로만 조회한다.
        // 곡이 사라지면 꺼내 볼 방법이 없으므로 같이 지우는 게 맞다.
        activityLogRepository.deleteBySongId(songId);

        // 자리(slot)는 Song 에 cascade = ALL, orphanRemoval = true 로 묶여 있어
        // 따로 지우지 않아도 함께 사라진다.
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