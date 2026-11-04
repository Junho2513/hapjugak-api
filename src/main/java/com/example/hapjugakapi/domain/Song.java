package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String artist;

    @Column(length = 500)
    private String youtubeUrl;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposer_id", nullable = false)
    private Member proposer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Slot> slots = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 자리가 다 찬 <b>순간</b>. 성립이 풀리면 지운다.
     *
     * <p>화면은 성립한 곡을 이 시각 순서로 줄 세운다 — 먼저 성립한 곡이 위다.
     * 곡을 올린 순서로 줄 세우면, 나중에 올린 곡이 먼저 성립해도 아래에 남아
     * "방금 각이 나왔는데 왜 저 밑에 있지?" 가 된다.
     *
     * <p>풀릴 때 지우는 것이 중요하다. 그래야 다시 성립했을 때 원래 자리로
     * 돌아가지 않고 성립 곡들의 맨 아래로 붙는다 — 그게 사람이 본 순서와 맞다.
     *
     * <p>화면에서만 기억할 수는 없다. 새로고침하거나 다른 사람이 보면 순서가 달라진다.
     */
    private LocalDateTime completedAt;

    protected Song() {
    }

    public Song(String title, String artist, String youtubeUrl, String description, Member proposer) {
        this.title = title;
        this.artist = artist;
        this.youtubeUrl = youtubeUrl;
        this.description = description;
        this.proposer = proposer;
    }

    /** 자리를 하나 붙인다. 종류는 이름에서 짐작한다. */
    public void addSlot(String label) {
        slots.add(new Slot(this, label, PartNames.kindOf(label), slots.size()));
    }

    /**
     * 편성(파트 구성)을 주어진 이름 목록에 맞춘다.
     *
     * <p>목록에서 빠진 자리는 이미 사람이 들어와 있어도 함께 비운다.
     * "모집하지 않는 파트에는 자리가 남지 않는다"가 이 서비스의 규칙이다.
     *
     * <p>이름이 그대로 남은 자리는 <b>객체를 그대로 둔다</b>. 지우고 다시 만들면
     * 그 자리에 들어와 있던 사람이 조용히 빠진다. 편성에서 「기타2」를 지웠을 뿐인데
     * 「보컬」을 맡은 사람까지 사라지는 일은 없어야 한다.
     */
    public void syncParts(Collection<String> labels) {
        if (labels == null) return;
        List<String> wanted = new ArrayList<>();
        for (String raw : labels) {
            if (raw == null) continue;
            String label = raw.trim();
            if (!label.isEmpty() && !wanted.contains(label)) wanted.add(label);
        }
        slots.removeIf(slot -> !wanted.contains(slot.getLabel()));
        for (int i = 0; i < wanted.size(); i++) {
            String label = wanted.get(i);
            Slot found = null;
            for (Slot slot : slots) {
                if (slot.getLabel().equals(label)) { found = slot; break; }
            }
            if (found == null) {
                slots.add(new Slot(this, label, PartNames.kindOf(label), i));
            } else {
                found.moveTo(i);
            }
        }
        slots.sort(java.util.Comparator.comparingInt(Slot::getOrd));
    }

    public void assignTo(Event event) {
        this.event = event;
    }

    public void unassign() {
        this.event = null;
    }

    public boolean isCandidate() {
        return event == null;
    }

    public long getFilledCount() {
        return slots.stream().filter(Slot::isTaken).count();
    }

    public boolean isComplete() {
        return !slots.isEmpty() && getFilledCount() == slots.size();
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * 자리가 바뀐 뒤에 반드시 부른다. 성립한 순간을 적고, 풀리면 지운다.
     *
     * <p>한 곳에서만 판단한다. 자리를 잡는 곳·비우는 곳·넘겨받는 곳마다
     * 따로 적으면 언젠가 한 군데를 빠뜨린다.
     */
    public void refreshCompletion() {
        if (isComplete()) {
            if (completedAt == null) completedAt = LocalDateTime.now();
        } else {
            completedAt = null;
        }
    }

    public boolean isProposedBy(Member member) {
        return proposer.getId().equals(member.getId());
    }

    /** Firestore 이관 전용. */
    public void restoreCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public String getDescription() {
        return description;
    }

    public Member getProposer() {
        return proposer;
    }

    public Event getEvent() {
        return event;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}