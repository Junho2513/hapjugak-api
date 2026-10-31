package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.ActivityLogResponse;
import com.example.hapjugakapi.service.ActivityLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/songs/{songId}/logs")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public List<ActivityLogResponse> findBySong(@PathVariable Long songId) {
        return activityLogService.findBySong(songId);
    }

    @PostMapping
    public ActivityLogResponse create(@PathVariable Long songId,
                                      @RequestBody ActivityLogRequest request,
                                      @AuthenticationPrincipal Long memberId) {
        return activityLogService.create(songId, request, memberId);
    }
}