package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.OnCreate;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 일정에 속한 곡.
 *
 * 주소가 /songs 가 아니라 /events/{eventId}/songs 인 이유 —
 * 곡은 혼자 존재하지 않는다. 반드시 어느 합주에서 할 곡이다.
 * 그 관계를 주소가 그대로 말해주는 편이, 질의 문자열로 거르는 것보다 정직하다.
 *
 *   전   POST /songs            → 떠다니는 곡이 생김
 *        PATCH /songs/3/event   → 나중에 일정에 끼워 넣음
 *   후   POST /events/7/songs   → 처음부터 그 합주의 곡
 */
@Tag(name = "일정의 곡", description = "합주 일정 안에서 곡을 올리고 봅니다")
@RestController
@RequestMapping("/events/{eventId}/songs")
public class EventSongController {

    private final SongService songService;

    public EventSongController(SongService songService) {
        this.songService = songService;
    }

    /**
     * 비공개 일정이어도 막지 않는다.
     * 단건 조회(GET /events/{id})와 같은 규칙이다 — 주소를 아는 것이 곧 초대를 받은 것이다.
     * 목록(GET /events, GET /songs)에서 가리는 것과는 다른 이야기다.
     */
    @Operation(summary = "이 합주의 곡 목록",
            description = "비공개 일정이어도 주소를 아는 사람은 볼 수 있습니다. 목록에 뜨지 않을 뿐입니다.")
    @ApiResponse(responseCode = "200", description = "곡 목록",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = SongResponse.class))))
    @ApiResponse(responseCode = "404", description = "합주 일정을 찾을 수 없습니다")
    @GetMapping
    public List<SongResponse> find(@PathVariable Long eventId) {
        return songService.findByEvent(eventId);
    }

    @Operation(summary = "이 합주에 곡 올리기",
            description = "parts 를 비워 보내면 그 합주의 기본 편성대로 자리가 만들어집니다. "
                    + "마감됐거나 끝난 합주에는 올릴 수 없고, 1인당 최대 곡수를 넘겨도 막힙니다.")
    @ApiResponse(responseCode = "201", description = "곡이 만들어졌습니다")
    @ApiResponse(responseCode = "400", description = "입력값이 규칙에 맞지 않습니다")
    @ApiResponse(responseCode = "404", description = "합주 일정을 찾을 수 없습니다")
    @ApiResponse(responseCode = "409", description = "마감됐거나, 1인당 곡 수를 넘겼습니다")
    @PostMapping
    public ResponseEntity<SongResponse> create(
            @PathVariable Long eventId,
            @Validated({Default.class, OnCreate.class}) @RequestBody SongRequest request,
            @AuthenticationPrincipal Long memberId) {

        SongResponse created = songService.createIn(eventId, request, memberId);

        // Location 은 곡 자체의 주소를 가리킨다.
        // 만들어진 자리는 /songs/{id} 로 다루므로, 여기서 만든 주소를 그대로 쓰면 안 된다.
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/songs/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
