package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.config.Viewer;
import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.dto.OnCreate;
import com.example.hapjugakapi.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "합주 일정", description = "합주를 열고 · 고치고 · 마감합니다")
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "합주 열기")
    @ApiResponse(responseCode = "201", description = "일정이 만들어졌습니다")
    @ApiResponse(responseCode = "400", description = "입력값이 규칙에 맞지 않습니다")
    @PostMapping
    public ResponseEntity<EventResponse> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody EventRequest request,
            @AuthenticationPrincipal Long memberId) {
        EventResponse created = eventService.create(request, memberId);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    /**
     *   GET /events                   전체
     *   GET /events?completed=true    마감된 일정
     *   GET /events?completed=false   진행 중인 일정
     */
    @Operation(summary = "일정 목록",
            description = "completed 를 주지 않으면 전체, true 면 마감된 것, false 면 진행 중인 것만 돌려줍니다. "
                    + "비공개 일정은 주최자 본인, 이미 자리를 잡은 참가자, 관리자에게만 보입니다.")
    @GetMapping
    public List<EventResponse> find(@RequestParam(required = false) Boolean completed) {
        return eventService.find(completed, Viewer.current());
    }

    /**
     * key 를 질의 문자열로 받는 이유 —
     * 경로에 넣으면(/events/7/Kx3f...) 캐시나 중계 서버의 접근 기록에 경로가 남기 쉽고,
     * 무엇보다 키 없이 부르는 공개 일정과 주소 모양이 갈라진다.
     * 선택 항목은 질의 문자열에 두는 편이 맞다.
     */
    @Operation(summary = "일정 하나 조회",
            description = "비공개 일정은 key 가 맞아야 열립니다. 주최자·관리자·이미 자리를 잡은 참가자는 key 없이도 볼 수 있습니다. "
                    + "키가 틀리면 404 입니다 — 403 은 그 자리에 무언가 있다는 것을 알려주기 때문입니다.")
    @ApiResponse(responseCode = "404", description = "합주 일정을 찾을 수 없거나, 비공개 일정의 키가 맞지 않습니다")
    @GetMapping("/{id}")
    public EventResponse findById(@PathVariable Long id,
                                  @RequestParam(required = false) String key) {
        return eventService.findById(id, key, Viewer.current());
    }

    @Operation(summary = "일정 수정", description = "보낸 항목만 바뀝니다. 호스트와 관리자만 할 수 있습니다.")
    @PatchMapping("/{id}")
    public EventResponse update(@PathVariable Long id,
                                @Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal Long memberId) {
        return eventService.update(id, request, memberId);
    }

    /**
     * 일정을 마감 상태로 바꾼다.
     *
     * 자원의 일부(상태)만 바꾸므로 PATCH 가 맞다.
     * 바뀐 일정을 그대로 돌려주므로 200 이다.
     */
    @Operation(summary = "합주 마감", description = "마감하면 곡을 더 넣거나 뺄 수 없습니다.")
    @PatchMapping("/{id}/complete")
    public EventResponse complete(@PathVariable Long id,
                                  @AuthenticationPrincipal Long memberId) {
        return eventService.complete(id, memberId);
    }

    @Operation(summary = "일정 삭제",
            description = "이 일정의 곡과 자리, 활동 기록도 함께 지워집니다. 되돌릴 수 없습니다. 주최자와 관리자만 할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "삭제되었습니다")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal Long memberId) {
        eventService.delete(id, memberId);
    }

    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
