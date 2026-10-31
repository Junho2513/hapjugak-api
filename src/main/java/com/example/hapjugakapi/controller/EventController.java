package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.EventRequest;
import com.example.hapjugakapi.dto.EventResponse;
import com.example.hapjugakapi.service.EventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public EventResponse create(@RequestBody EventRequest request,
                                @AuthenticationPrincipal Long memberId) {
        return eventService.create(request, memberId);
    }

    @GetMapping
    public List<EventResponse> findAll() {
        return eventService.findAll();
    }

    @GetMapping("/completed")
    public List<EventResponse> findCompleted() {
        return eventService.findCompleted();
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

    @PatchMapping("/{id}/complete")
    public EventResponse complete(@PathVariable Long id,
                                  @AuthenticationPrincipal Long memberId) {
        return eventService.complete(id, memberId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal Long memberId) {
        eventService.delete(id, memberId);
    }
}