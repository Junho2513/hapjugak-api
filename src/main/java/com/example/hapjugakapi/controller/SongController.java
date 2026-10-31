package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.dto.SlotRequest;
import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.dto.SongSearchCondition;
import com.example.hapjugakapi.service.SongService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping
    public SongResponse create(@RequestBody SongRequest request,
                               @AuthenticationPrincipal Long memberId) {
        return songService.create(request, memberId);
    }

    @GetMapping("/candidates")
    public List<SongResponse> findCandidates() {
        return songService.findCandidates();
    }

    @GetMapping("/search")
    public List<SongResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Part openPart,
            @RequestParam(required = false) Boolean candidateOnly,
            @RequestParam(required = false) Long eventId
    ) {
        return songService.search(
                new SongSearchCondition(keyword, openPart, candidateOnly, eventId)
        );
    }

    @GetMapping
    public List<SongResponse> find(@RequestParam(required = false) Long eventId) {
        return eventId == null ? songService.findAll() : songService.findByEvent(eventId);
    }

    @GetMapping("/{id}")
    public SongResponse findById(@PathVariable Long id) {
        return songService.findById(id);
    }

    @PatchMapping("/{songId}")
    public SongResponse update(@PathVariable Long songId,
                               @RequestBody SongRequest request,
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
                                    @RequestBody SlotRequest request,
                                    @AuthenticationPrincipal Long memberId) {
        return songService.setHandover(songId, slotId, memberId, request.open());
    }

    @PutMapping("/{songId}/slots/{slotId}")
    public SongResponse assignSlot(@PathVariable Long songId,
                                   @PathVariable Long slotId,
                                   @RequestBody SlotRequest request,
                                   @AuthenticationPrincipal Long memberId) {
        return songService.assignSlot(songId, slotId, request.memberId(), request.open(), memberId);
    }

    @DeleteMapping("/{songId}")
    public void delete(@PathVariable Long songId,
                       @AuthenticationPrincipal Long memberId) {
        songService.delete(songId, memberId);
    }
}