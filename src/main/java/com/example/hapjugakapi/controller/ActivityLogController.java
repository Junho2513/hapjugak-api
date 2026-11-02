package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.ActivityLogResponse;
import com.example.hapjugakapi.service.ActivityLogService;
import org.springframework.http.HttpStatus;
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

    /**
     * 활동 로그 한 건을 남긴다.
     *
     * 새 자원을 만드는 것이므로 201 이지만, Location 헤더는 붙이지 않는다.
     * 로그를 한 건씩 조회하는 주소(GET /songs/{songId}/logs/{id})가 없기 때문이다.
     * 열리지도 않는 주소를 Location 으로 알려주는 쪽이 아무것도 안 주는 것보다 나쁘다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityLogResponse create(@PathVariable Long songId,
                                      @RequestBody ActivityLogRequest request,
                                      @AuthenticationPrincipal Long memberId) {
        return activityLogService.create(songId, request, memberId);
    }
}
