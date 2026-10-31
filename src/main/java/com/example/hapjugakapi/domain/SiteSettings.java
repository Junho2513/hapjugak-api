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

    /** S3에 올라간 배너 이미지의 공개 주소. 이미지 자체는 DB에 두지 않는다. */
    @Column(length = 500)
    private String heroUrl;

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

    public String getHeroUrl() {
        return heroUrl;
    }

    public void setHeroUrl(String heroUrl) {
        this.heroUrl = heroUrl;
    }
}
