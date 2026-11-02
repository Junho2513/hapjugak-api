package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@RequestBody EventRequest request,
                                                @AuthenticationPrincipal Long memberId) {
        EventResponse created = eventService.create(request, memberId);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    /**
     *   GET /events                   전체
     *   GET /events?completed=true    마감된 일정
     *   GET /events?completed=false   진행 중인 일정
     */
    @GetMapping
    public List<EventResponse> find(@RequestParam(required = false) Boolean completed) {
        return eventService.find(completed);
    }

    @GetMapping("/{id}")
    public EventResponse findById(@PathVariable Long id) {
        return eventService.findById(id);
    }

    @PatchMapping("/{id}")
    public EventResponse update(@PathVariable Long id,
                                @RequestBody EventRequest request,
                                @AuthenticationPrincipal Long memberId) {
        return eventService.update(id, request, memberId);
    }

    /**
     * 일정을 마감 상태로 바꾼다.
     *
     * 자원의 일부(상태)만 바꾸므로 PATCH 가 맞다.
     * 바뀐 일정을 그대로 돌려주므로 200 이다.
     */
    @PatchMapping("/{id}/complete")
    public EventResponse complete(@PathVariable Long id,
                                  @AuthenticationPrincipal Long memberId) {
        return eventService.complete(id, memberId);
    }

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
