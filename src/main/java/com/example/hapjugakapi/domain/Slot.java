package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

@Entity
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 자리를 잡았지만 다른 사람에게 넘겨줄 의사가 있음(양도 가능). */
    @Column(nullable = false)
    private boolean openToHandover = false;

    protected Slot() {
    }

    public Slot(Song song, Part part) {
        this.song = song;
        this.part = part;
    }

    public boolean isTaken() {
        return member != null;
    }

    public void claimBy(Member member) {
        if (isTaken()) throw new IllegalStateException("이미 찬 자리입니다.");
        this.member = member;
    }

    public void claimBy(Member member, boolean openToHandover) {
        claimBy(member);
        this.openToHandover = openToHandover;
    }

    /** 관리자/주최자가 자리를 강제로 지정한다. 이미 찬 자리도 덮어쓴다. */
    public void assignTo(Member member, boolean openToHandover) {
        this.member = member;
        this.openToHandover = member != null && openToHandover;
    }

    /** 양도 가능으로 표시된 자리를 다른 사람이 넘겨받는다. */
    public void takeOverBy(Member member) {
        if (!isTaken()) throw new IllegalStateException("비어 있는 자리입니다.");
        if (!openToHandover) throw new IllegalStateException("양도 가능한 자리가 아닙니다.");
        this.member = member;
        this.openToHandover = false;
    }

    public void setOpenToHandover(boolean openToHandover) {
        this.openToHandover = openToHandover;
    }

    public void release() {
        this.member = null;
        this.openToHandover = false;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Song getSong() {
        return song;
    }

    public Part getPart() {
        return part;
    }

    public Member getMember() {
        return member;
    }

    public boolean isOpenToHandover() {
        return openToHandover;
    }
}