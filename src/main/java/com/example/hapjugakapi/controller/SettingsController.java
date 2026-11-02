package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.SettingsDto;
import com.example.hapjugakapi.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사이트 설정", description = "메인 배너 같은 전체 설정")
@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Operation(summary = "설정 조회")
    @GetMapping
    public SettingsDto find() {
        return settingsService.find();
    }

    @Operation(summary = "설정 수정", description = "관리자만 할 수 있습니다.")
    @PutMapping
    public SettingsDto update(@RequestBody SettingsDto request,
                              @AuthenticationPrincipal Long memberId) {
        return settingsService.update(request, memberId);
    }
}