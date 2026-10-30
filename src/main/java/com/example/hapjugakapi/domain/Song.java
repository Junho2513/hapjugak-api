package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String artist;

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

    protected Song() {}

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

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getDescription() { return description; }
    public Member getProposer() { return proposer; }
    public Event getEvent() { return event; }
    public List<Slot> getSlots() { return slots; }
}