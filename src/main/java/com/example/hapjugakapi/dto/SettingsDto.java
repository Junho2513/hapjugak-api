package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.SiteSettings;

public record SettingsDto(
        String heroImage
) {
    public static SettingsDto from(SiteSettings settings) {
        return new SettingsDto(settings.getHeroImage());
    }
}