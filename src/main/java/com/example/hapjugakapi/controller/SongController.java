package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.SlotRequest;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.OnCreate;
import com.example.hapjugakapi.dto.SongSearchCondition;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import com.example.hapjugakapi.service.SongService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping
    public ResponseEntity<SongResponse> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody SongRequest request,
            @AuthenticationPrincipal Long memberId) {
        SongResponse created = songService.create(request, memberId);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    /**
     * 곡 목록. 네 가지 조건으로 거를 수 있고, 조건이 없으면 전체를 돌려준다.
     *
     *   GET /songs                        전체
     *   GET /songs?candidateOnly=true     아직 일정에 안 들어간 곡
     *   GET /songs?eventId=3              특정 일정의 곡
     *   GET /songs?keyword=&openPart=     검색
     *
     * 전에는 /songs, /songs/candidates, /songs/search 세 개로 나뉘어 있었다.
     * 셋 다 "곡 목록"이라는 같은 자원을 돌려주면서 거르는 방법만 달랐으므로,
     * 경로를 나눌 이유가 없었다. 컬렉션을 거르는 일은 질의 문자열의 몫이다.
     *
     * 합치면서 전체 조회도 fetchJoin 이 들어 있는 경로를 타게 됐다.
     */
    @GetMapping
    public List<SongResponse> find(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Part openPart,
            @RequestParam(required = false) Boolean candidateOnly,
            @RequestParam(required = false) Long eventId
    ) {
        return songService.search(
                new SongSearchCondition(keyword, openPart, candidateOnly, eventId)
        );
    }

    @GetMapping("/{id}")
    public SongResponse findById(@PathVariable Long id) {
        return songService.findById(id);
    }

    @PatchMapping("/{songId}")
    public SongResponse update(@PathVariable Long songId,
                               @Valid @RequestBody SongRequest request,
                               @AuthenticationPrincipal Long memberId) {
        return songService.update(songId, request, memberId);
    }

    @PatchMapping("/{songId}/event/{eventId}")
    public SongResponse assignToEvent(@PathVariable Long songId, @PathVariable Long eventId) {
        return songService.assignToEvent(songId, eventId);
    }

    @PatchMapping("/{songId}/event")
    public SongResponse unassign(@PathVariable Long songId) {
        return songService.unassign(songId);
    }

    // ───────────────────────────────────────────────────────────────
    // 자리(slot) 관련 엔드포인트
    //
    // 아래 네 개는 새 자원을 만들지 않는다. 이미 존재하는 자리의 주인이 바뀔 뿐이다.
    // 그래서 201 이 아니라 200 이고, 바뀐 곡 전체를 돌려준다.
    // 자리 하나만 바뀌어도 "몇 자리가 찼는지", "성립됐는지"가 함께 달라지므로
    // 클라이언트가 곡 전체를 다시 그릴 수 있어야 한다.
    //
    // 해제(releaseSlot)도 DELETE 지만 204 가 아니다.
    // 204 는 "돌려줄 내용이 없다"는 뜻인데, 여기서는 바뀐 곡을 돌려주기 때문이다.
    // ───────────────────────────────────────────────────────────────

    @PostMapping("/{songId}/slots/{slotId}/claim")
    public SongResponse claimSlot(@PathVariable Long songId,
                                  @PathVariable Long slotId,
                                  @RequestBody(required = false) SlotRequest request,
                                  @AuthenticationPrincipal Long memberId) {
        boolean open = request != null && request.open();
        return songService.claimSlot(songId, slotId, memberId, open);
    }

    @DeleteMapping("/{songId}/slots/{slotId}/claim")
    public SongResponse releaseSlot(@PathVariable Long songId,
                                    @PathVariable Long slotId,
                                    @AuthenticationPrincipal Long memberId) {
        return songService.releaseSlot(songId, slotId, memberId);
    }

    @PostMapping("/{songId}/slots/{slotId}/takeover")
    public SongResponse takeOverSlot(@PathVariable Long songId,
                                     @PathVariable Long slotId,
                                     @AuthenticationPrincipal Long memberId) {
        return songService.takeOverSlot(songId, slotId, memberId);
    }

    @PatchMapping("/{songId}/slots/{slotId}/handover")
    public SongResponse setHandover(@PathVariable Long songId,
                                    @PathVariable Long slotId,
                                    @Valid @RequestBody SlotRequest request,
                                    @AuthenticationPrincipal Long memberId) {
        return songService.setHandover(songId, slotId, memberId, request.open());
    }

    @PutMapping("/{songId}/slots/{slotId}")
    public SongResponse assignSlot(@PathVariable Long songId,
                                   @PathVariable Long slotId,
                                   @Valid @RequestBody SlotRequest request,
                                   @AuthenticationPrincipal Long memberId) {
        return songService.assignSlot(songId, slotId, request.memberId(), request.open(), memberId);
    }

    @DeleteMapping("/{songId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long songId,
                       @AuthenticationPrincipal Long memberId) {
        songService.delete(songId, memberId);
    }

    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
