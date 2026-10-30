package com.example.hapjugakapi;

import jakarta.persistence.*;

@Entity
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    protected Slot() {}

    public Slot(Song song, Part part) {
        this.song = song;
        this.part = part;
    }

    public boolean isTaken() {
        return member != null;
    }

    public void claimBy(Member member) {
        if (isTaken()) {
            throw new IllegalStateException("이미 찬 자리입니다.");
        }
        this.member = member;
    }

    public void release() {
        this.member = null;
    }

    public Long getId() { return id; }
    public Song getSong() { return song; }
    public Part getPart() { return part; }
    public Member getMember() { return member; }
}