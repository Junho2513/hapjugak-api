package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 탈퇴하면 NULL 이 된다. 그래서 nullable 이다.
     *
     * UNIQUE 는 그대로 둔다. MySQL 은 NULL 을 여러 개 허용하므로 탈퇴자가
     * 몇 명이든 걸리지 않고, 같은 주소로 다시 가입하는 길도 열려 있다.
     */
    @Column(unique = true)
    private String email;

    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    private String providerId;

    private String area;

    @Column(length = 1000)
    private String bio;

    /** S3에 올라간 이미지의 공개 주소. 이미지 자체는 DB에 두지 않는다. */
    @Column(length = 500)
    private String photoUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_instrument",
            joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument", length = 20)
    private Set<Instrument> instruments = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 탈퇴한 시각. 차 있으면 탈퇴한 회원이다. */
    private LocalDateTime deletedAt;

    /** 탈퇴한 회원이 화면에 나타날 때 쓰는 이름. */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    protected Member() {
    }

    public Member(String email, String password, String nickname, String area, String bio) {
        this.email = email;
        this.password = password;
        this.nickname = trim(nickname);
        this.area = area;
        this.bio = bio;
        this.provider = AuthProvider.LOCAL;
    }

    /**
     * 소셜로 처음 들어온 사람.
     *
     * 전에는 google(...) 하나였다. 카카오·네이버가 붙으면서 제공자를 받는다.
     *
     * 이메일이 null 일 수 있다. 카카오와 네이버는 이메일 제공이 선택 동의라
     * 사용자가 거부하면 아예 오지 않는다. 그래도 가입은 되어야 한다 —
     * 이메일은 나중에 프로필에서 채울 수 있다.
     */
    public static Member social(AuthProvider provider, String email,
                                String nickname, String providerId) {
        Member member = new Member();
        member.email = email;
        member.nickname = trim(nickname);
        member.provider = provider;
        member.providerId = providerId;
        return member;
    }

    private static String trim(String nickname) {
        if (nickname == null || nickname.isBlank()) return "회원";
        return nickname.length() > 20 ? nickname.substring(0, 20) : nickname;
    }

    /**
     * 자체 가입했던 계정에 소셜 계정을 이어붙인다.
     *
     * 부르는 쪽(AuthService)이 "이어도 되는 사이인가"를 먼저 판단한다.
     * 여기서는 묻지 않는다 — 판단 기준이 제공자마다 다르고, 그 판단은
     * 엔티티가 아니라 서비스의 일이다.
     */
    public void linkSocial(AuthProvider provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
    }

    public boolean hasPassword() {
        return password != null;
    }

    public boolean isLinkedWithSocial() {
        return providerId != null;
    }

    public void addInstrument(Instrument instrument) {
        this.instruments.add(instrument);
    }

    /** Firestore 이관 전용. 원래 가입일을 살리기 위해서만 사용한다. */
    public void restoreCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 탈퇴 — 행은 남기고 사람을 지운다.
     *
     * 행을 지울 수 없는 이유는 V8 마이그레이션에 적어두었다. 간단히는,
     * 이 사람이 연 합주와 올린 곡이 member.id 를 가리키고 있어서
     * 지우면 DB 가 거부하거나(지금) 남의 기록까지 끌려 내려간다(CASCADE).
     *
     * 그래서 남길 것과 지울 것을 나눈다.
     *   남는 것 — id, 가입일, 「탈퇴한 사용자」라는 이름.
     *             이 셋으로는 누구였는지 알 수 없다.
     *   지우는 것 — 이메일, 비밀번호, 구글 식별자, 지역, 소개, 사진, 악기.
     *
     * 구글 식별자(providerId)를 같이 비우는 것이 중요하다. 이메일만 지우고
     * 두면 같은 구글 계정으로 다시 들어왔을 때 그 값으로 옛 사람과 이어질 수 있다.
     *
     * 역할도 USER 로 되돌린다. 관리자가 탈퇴한 뒤에도 관리자로 남아 있을 이유가 없다.
     *
     * 사진 파일 자체는 S3 에 있다. 이 메서드는 주소만 지우므로,
     * 부르는 쪽(MemberService)이 파일도 지워야 한다.
     * 지울 주소를 알아야 하니 돌려준다.
     *
     * @return 지워야 할 사진 주소. 사진이 없었으면 null.
     */
    public String withdraw(LocalDateTime now) {
        String photoToDelete = this.photoUrl;

        this.email = null;
        this.password = null;
        this.providerId = null;
        this.area = null;
        this.bio = null;
        this.photoUrl = null;
        this.instruments.clear();
        this.nickname = WITHDRAWN_NICKNAME;
        this.role = Role.USER;
        this.deletedAt = now;

        return photoToDelete;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getArea() {
        return area;
    }

    public String getBio() {
        return bio;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public Set<Instrument> getInstruments() {
        return instruments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setNickname(String nickname) {
        this.nickname = trim(nickname);
    }

    public void setArea(String area) {
        this.area = area;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
