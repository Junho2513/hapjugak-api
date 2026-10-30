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

    @Column(nullable = false, unique = true)
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

    @Column(columnDefinition = "LONGTEXT")
    private String photoImage;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_instrument",
            joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument", length = 20)
    private Set<Instrument> instruments = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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

    public static Member google(String email, String nickname, String providerId) {
        Member member = new Member();
        member.email = email;
        member.nickname = trim(nickname);
        member.provider = AuthProvider.GOOGLE;
        member.providerId = providerId;
        return member;
    }

    private static String trim(String nickname) {
        if (nickname == null || nickname.isBlank()) return "회원";
        return nickname.length() > 20 ? nickname.substring(0, 20) : nickname;
    }

    public void linkGoogle(String providerId) {
        this.providerId = providerId;
    }

    public boolean hasPassword() {
        return password != null;
    }

    public boolean isLinkedWithGoogle() {
        return providerId != null;
    }

    public void addInstrument(Instrument instrument) {
        this.instruments.add(instrument);
    }

    /** Firestore 이관 전용. 원래 가입일을 살리기 위해서만 사용한다. */
    public void restoreCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    public String getPhotoImage() {
        return photoImage;
    }

    public Set<Instrument> getInstruments() {
        return instruments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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

    public void setPhotoImage(String photoImage) {
        this.photoImage = photoImage;
    }
}