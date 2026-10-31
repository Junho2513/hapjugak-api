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

    /** 옛 저장 방식. 이미지 자체(base64)가 이 컬럼에 들어 있었다. V3에서 제거한다. */
    @Column(columnDefinition = "LONGTEXT")
    private String heroImage;

    /** 지금 방식. S3에 올라간 이미지의 공개 주소만 저장한다. */
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

    public String getHeroImage() {
        return heroImage;
    }

    public String getHeroUrl() {
        return heroUrl;
    }

    /** 이관 전이면 옛 base64, 후면 S3 주소. */
    public String getHeroForDisplay() {
        return heroUrl != null ? heroUrl : heroImage;
    }

    /** Firestore 이관 전용. 새 코드에서는 쓰지 않는다. */
    public void setHeroImage(String heroImage) {
        this.heroImage = heroImage;
    }

    /** 새 주소를 넣으면서 옛 base64를 같이 비운다. */
    public void setHeroUrl(String heroUrl) {
        this.heroUrl = heroUrl;
        this.heroImage = null;
    }
}
