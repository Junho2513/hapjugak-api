package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.SongRequest;
import com.example.hapjugakapi.dto.SongResponse;
import com.example.hapjugakapi.service.SongService;
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
    public SongResponse create(@RequestBody SongRequest request) {
        return songService.create(request);
    }

    @GetMapping("/candidates")
    public List<SongResponse> findCandidates() {
        return songService.findCandidates();
    }

    @GetMapping
    public List<SongResponse> findByEvent(@RequestParam Long eventId) {
        return songService.findByEvent(eventId);
    }

    @GetMapping("/{id}")
    public SongResponse findById(@PathVariable Long id) {
        return songService.findById(id);
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
                                  @RequestParam Long memberId) {
        return songService.claimSlot(songId, slotId, memberId);
    }

    @DeleteMapping("/{songId}/slots/{slotId}/claim")
    public SongResponse releaseSlot(@PathVariable Long songId,
                                    @PathVariable Long slotId,
                                    @RequestParam Long memberId) {
        return songService.releaseSlot(songId, slotId, memberId);
    }

    @DeleteMapping("/{songId}")
    public void delete(@PathVariable Long songId, @RequestParam Long memberId) {
        songService.delete(songId, memberId);
    }
}