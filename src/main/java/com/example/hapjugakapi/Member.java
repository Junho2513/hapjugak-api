package com.example.hapjugakapi;

import jakarta.persistence.*;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    private String area;

    @Column(length = 1000)
    private String bio;

    protected Member() {}

    public Member(String email, String nickname, String area, String bio) {
        this.email = email;
        this.nickname = nickname;
        this.area = area;
        this.bio = bio;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getArea() { return area; }
    public String getBio() { return bio; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setArea(String area) { this.area = area; }
    public void setBio(String bio) { this.bio = bio; }
}