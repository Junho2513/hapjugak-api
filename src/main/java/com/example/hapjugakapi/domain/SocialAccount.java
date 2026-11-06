package com.example.hapjugakapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 회원과 소셜 계정의 연결 하나.
 *
 * <p>전에는 member 테이블의 칸 두 개(provider, provider_id)였다. 칸이 하나씩이라
 * 회원 한 명이 소셜 계정을 하나만 들 수 있었고, 그래서 카카오와 네이버를 둘 다
 * 쓰는 사람은 둘 중 하나를 포기해야 했다.
 *
 * <p>행으로 만들면 여럿을 들 수 있다. 제약 두 개가 그 "여럿"의 모양을 정한다.
 * <ul>
 *   <li>(provider, provider_id) 유니크 — 하나의 소셜 계정은 한 회원에게만 붙는다
 *   <li>(member_id, provider) 유니크 — 한 회원은 제공자당 하나만 든다
 * </ul>
 */
@Entity
@Table(name = "social_account", uniqueConstraints = {
        @UniqueConstraint(name = "ux_social_identity", columnNames = {"provider", "provider_id"}),
        @UniqueConstraint(name = "ux_social_member_provider", columnNames = {"member_id", "provider"})
})
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 그 제공자 안에서의 고유 번호. 구글은 sub, 카카오는 id, 네이버는 response.id. */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    protected SocialAccount() {
    }

    public SocialAccount(Member member, AuthProvider provider, String providerId) {
        this.member = member;
        this.provider = provider;
        this.providerId = providerId;
        this.linkedAt = LocalDateTime.now();
    }

    /**
     * 같은 제공자인데 고유 번호가 달라졌을 때 쓴다.
     *
     * <p>Firestore 에서 옮겨온 계정들이 그랬다. provider 는 GOOGLE 인데
     * provider_id 에는 구글의 sub 가 아니라 Firebase UID 가 들어 있었다.
     * 진짜 sub 는 그 사람이 로그인해 봐야 알 수 있으므로, 알게 된 순간 고쳐 둔다.
     */
    public void changeProviderId(String providerId) {
        this.providerId = providerId;
        this.linkedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }
}
