package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.ActivityLogResponse;
import com.example.hapjugakapi.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "활동 기록", description = "곡에서 일어난 일을 남기고 봅니다")
@RestController
@RequestMapping("/songs/{songId}/logs")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Operation(summary = "곡의 활동 기록", description = "최근 200건을 최신순으로 돌려줍니다.")
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
    @Operation(summary = "활동 기록 남기기", description = "기록에는 그 시점의 닉네임과 곡 제목이 복제되어 저장됩니다. 나중에 이름이 바뀌어도 기록은 그대로 남습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityLogResponse create(@PathVariable Long songId,
                                      @Valid @RequestBody ActivityLogRequest request,
                                      @AuthenticationPrincipal Long memberId) {
        return activityLogService.create(songId, request, memberId);
    }
}
