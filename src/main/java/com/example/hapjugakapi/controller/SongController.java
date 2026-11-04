package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.dto.SlotRequest;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.OnCreate;
import com.example.hapjugakapi.dto.SongSearchCondition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "곡과 자리", description = "곡을 올리고 · 일정에 넣고 · 파트 자리를 잡습니다")
@RestController
@RequestMapping("/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    /**
     * 일정 없이 곡을 만든다. 없어질 예정이다.
     *
     * 화면이 아직 이 주소로 곡을 올린다. 지금 지우면 배포하는 순간 곡 올리기가 죽는다.
     * 새 화면이 POST /events/{id}/songs 를 쓰도록 바뀐 뒤,
     * song.event_id 를 NOT NULL 로 조이는 V5 와 함께 지운다.
     */
    @Deprecated(forRemoval = true)
    @Operation(summary = "곡 올리기 (없어질 예정)",
            deprecated = true,
            description = "일정 안에서 올리려면 POST /events/{eventId}/songs 를 쓰세요. "
                    + "이 주소는 화면이 전환되는 동안만 남아 있습니다.")
    @ApiResponse(responseCode = "201", description = "곡이 만들어졌습니다")
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
    @Operation(summary = "곡 목록", description = "조건을 주지 않으면 전체를 돌려줍니다. candidateOnly=true 면 아직 일정에 안 들어간 곡만, keyword 는 제목과 아티스트를 함께 찾습니다. "
            + "비공개 일정에 들어간 곡은 그 일정을 볼 수 있는 사람에게만 보입니다.")
    @GetMapping
    public List<SongResponse> find(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String openPart,
            @RequestParam(required = false) Boolean candidateOnly,
            @RequestParam(required = false) Long eventId
    ) {
        return songService.search(
                new SongSearchCondition(keyword, openPart, candidateOnly, eventId),
                Viewer.current()
        );
    }

    @Operation(summary = "곡 하나 조회")
    @GetMapping("/{id}")
    public SongResponse findById(@PathVariable Long id) {
        return songService.findById(id);
    }

    @Operation(summary = "곡 수정", description = "추천한 사람과 관리자만 할 수 있습니다.")
    @PatchMapping("/{songId}")
    public SongResponse update(@PathVariable Long songId,
                               @Valid @RequestBody SongRequest request,
                               @AuthenticationPrincipal Long memberId) {
        return songService.update(songId, request, memberId);
    }

    @Operation(summary = "곡을 다른 일정으로 옮기기",
            description = "마감됐거나 끝난 일정으로는 옮길 수 없습니다.")
    @PatchMapping("/{songId}/event/{eventId}")
    public SongResponse assignToEvent(@PathVariable Long songId, @PathVariable Long eventId) {
        return songService.assignToEvent(songId, eventId);
    }

    @Deprecated(forRemoval = true)
    @Operation(summary = "곡을 일정에서 빼기 (없어질 예정)",
            deprecated = true,
            description = "곡은 이제 반드시 일정에 속하므로 빼낼 곳이 없습니다. "
                    + "다른 일정으로 옮기거나, 곡을 지우세요.")
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

    @Operation(summary = "자리 잡기", description = "이미 찬 자리면 409 입니다. 동시에 두 명이 눌러도 한 명만 성공합니다.")
    @ApiResponse(responseCode = "409", description = "다른 사람이 먼저 잡았습니다")
    @PostMapping("/{songId}/slots/{slotId}/claim")
    public SongResponse claimSlot(@PathVariable Long songId,
                                  @PathVariable Long slotId,
                                  @RequestBody(required = false) SlotRequest request,
                                  @AuthenticationPrincipal Long memberId) {
        boolean open = request != null && request.open();
        return songService.claimSlot(songId, slotId, memberId, open);
    }

    @Operation(summary = "자리 비우기", description = "DELETE 지만 204 가 아닙니다. 자리를 비우면 곡 전체 상태가 바뀌므로 바뀐 곡을 돌려줍니다.")
    @DeleteMapping("/{songId}/slots/{slotId}/claim")
    public SongResponse releaseSlot(@PathVariable Long songId,
                                    @PathVariable Long slotId,
                                    @AuthenticationPrincipal Long memberId) {
        return songService.releaseSlot(songId, slotId, memberId);
    }

    @Operation(summary = "양도받기", description = "앞사람이 양도 표시를 해둔 자리만 가져올 수 있습니다.")
    @PostMapping("/{songId}/slots/{slotId}/takeover")
    public SongResponse takeOverSlot(@PathVariable Long songId,
                                     @PathVariable Long slotId,
                                     @AuthenticationPrincipal Long memberId) {
        return songService.takeOverSlot(songId, slotId, memberId);
    }

    @Operation(summary = "양도 표시 켜고 끄기")
    @PatchMapping("/{songId}/slots/{slotId}/handover")
    public SongResponse setHandover(@PathVariable Long songId,
                                    @PathVariable Long slotId,
                                    @Valid @RequestBody SlotRequest request,
                                    @AuthenticationPrincipal Long memberId) {
        return songService.setHandover(songId, slotId, memberId, request.open());
    }

    @Operation(summary = "자리에 사람 배치", description = "관리자용입니다. memberId 를 비우면 자리가 빕니다.")
    @PutMapping("/{songId}/slots/{slotId}")
    public SongResponse assignSlot(@PathVariable Long songId,
                                   @PathVariable Long slotId,
                                   @Valid @RequestBody SlotRequest request,
                                   @AuthenticationPrincipal Long memberId) {
        return songService.assignSlot(songId, slotId, request.memberId(), request.open(), memberId);
    }

    @Operation(summary = "곡 삭제", description = "곡에 달린 활동 기록도 함께 지워집니다.")
    @ApiResponse(responseCode = "204", description = "삭제되었습니다")
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
