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

    /**
     * 자리의 <b>종류</b>. 악기가 겹치는지 판단할 때만 쓴다
     * (한 사람이 같은 곡에서 기타와 베이스를 동시에 잡을 수는 없다).
     * 직접 적은 파트는 종류를 알 수 없으므로 비어 있다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Part part;

    /**
     * 화면에 보이는 <b>이름</b>. 「보컬1」, 「기타2」, 「색소폰」.
     *
     * <p>종류와 이름을 나눈 이유는 하나다 — 같은 파트를 여러 명 모집할 수 있어야 한다.
     * 전에는 종류가 곧 이름이어서, 보컬이 둘인 곡을 적을 방법이 없었다.
     * 한 곡 안에서는 이름이 겹치지 않는다(DB 유니크 제약).
     */
    @Column(nullable = false, length = 30)
    private String label;

    /** 적은 차례. 화면은 이 순서로 보여준다. (ord 는 MySQL 함수 이름과 헷갈려 피한다) */
    @Column(name = "sort_order", nullable = false)
    private int ord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 자리를 잡았지만 다른 사람에게 넘겨줄 의사가 있음(양도 가능). */
    @Column(nullable = false)
    private boolean openToHandover = false;

    protected Slot() {
    }

    public Slot(Song song, String label, Part part, int ord) {
        this.song = song;
        this.label = label;
        this.part = part;
        this.ord = ord;
    }

    /** 차례만 바꾼다. 이름이 같은 자리는 사람을 그대로 둔 채 순서만 옮길 수 있다. */
    public void moveTo(int ord) {
        this.ord = ord;
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

    public String getLabel() {
        return label;
    }

    public int getOrd() {
        return ord;
    }

    public Member getMember() {
        return member;
    }

    public boolean isOpenToHandover() {
        return openToHandover;
    }
}