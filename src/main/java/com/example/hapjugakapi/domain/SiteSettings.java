package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

/**
 * 사이트 전체 설정. 행이 하나만 존재한다(id = 1 고정).
 */
@Entity
public class SiteSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(columnDefinition = "LONGTEXT")
    private String heroImage;

    protected SiteSettings() {
    }

    public static SiteSettings createDefault() {
        SiteSettings settings = new SiteSettings();
        settings.id = SINGLETON_ID;
        return settings;
    }

    public Long getId() {
        return id;
    }

    public String getHeroImage() {
        return heroImage;
    }

    public void setHeroImage(String heroImage) {
        this.heroImage = heroImage;
    }
}