package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.SettingsDto;
import com.example.hapjugakapi.service.SettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsDto find() {
        return settingsService.find();
    }

    @PutMapping
    public SettingsDto update(@RequestBody SettingsDto request,
                              @AuthenticationPrincipal Long memberId) {
        return settingsService.update(request, memberId);
    }
}