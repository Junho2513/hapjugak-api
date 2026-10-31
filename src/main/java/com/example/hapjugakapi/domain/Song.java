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

    protected Song() {
    }

    public Song(String title, String artist, String youtubeUrl, String description, Member proposer) {
        this.title = title;
        this.artist = artist;
        this.youtubeUrl = youtubeUrl;
        this.description = description;
        this.proposer = proposer;
    }

    public void addSlot(Part part) {
        slots.add(new Slot(this, part));
    }

    /**
     * 편성(파트 구성)을 주어진 목록에 맞춘다.
     * 이미 사람이 들어온 자리는 목록에서 빠져도 지우지 않는다.
     * 편성을 고쳤다는 이유로 참여자를 말없이 쫓아내면 안 되기 때문이다.
     */
    public void syncParts(Collection<Part> parts) {
        if (parts == null) return;
        List<Part> wanted = new ArrayList<>(parts);
        slots.removeIf(slot -> !slot.isTaken() && !wanted.contains(slot.getPart()));
        for (Part part : wanted) {
            boolean exists = slots.stream().anyMatch(slot -> slot.getPart() == part);
            if (!exists) addSlot(part);
        }
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