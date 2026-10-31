package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.SiteSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {
}