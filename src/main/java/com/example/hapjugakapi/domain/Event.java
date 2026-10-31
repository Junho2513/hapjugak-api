package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
public class Event {

    public enum Visibility { PUBLIC, PRIVATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String title;

    private LocalDate date;

    private LocalDate deadline;

    private LocalTime timeStart;

    private LocalTime timeEnd;

    @Column(length = 30)
    private String region;

    @Column(length = 100)
    private String studio;

    @Column(length = 60)
    private String room;

    @Column(length = 40)
    private String genre;

    @Column(length = 200)
    private String fee;

    @Column(length = 2000)
    private String description;

    @Column(length = 40)
    private String afterparty;

    private int capacity;

    private int maxSongsPerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility = Visibility.PUBLIC;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_default_part",
            joinColumns = @JoinColumn(name = "event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "part", length = 20)
    private Set<Part> defaultParts = new LinkedHashSet<>();

    @Column(columnDefinition = "LONGTEXT")
    private String coverImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    private boolean completed = false;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Event() {
    }

    public Event(String title, LocalDate date, LocalDate deadline, String place, Member host) {
        this.title = title;
        this.date = date;
        this.deadline = deadline;
        this.studio = place;
        this.host = host;
    }

    public void complete() {
        this.completed = true;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isDeadlinePassed() {
        return deadline != null && LocalDate.now().isAfter(deadline);
    }

    public boolean isHostedBy(Member member) {
        return host.getId().equals(member.getId());
    }

    /** 합주실 + 룸을 합친 표시용 장소. */
    public String getPlace() {
        if (studio == null) return room;
        return (room == null || room.isBlank()) ? studio : studio + " " + room;
    }

    public void addDefaultPart(Part part) {
        this.defaultParts.add(part);
    }

    /** 기본 파트 구성을 통째로 교체한다. */
    public void replaceDefaultParts(Collection<Part> parts) {
        this.defaultParts.clear();
        if (parts != null) this.defaultParts.addAll(parts);
    }

    /** Firestore 이관 전용. */
    public void restoreTimestamps(LocalDateTime createdAt, LocalDateTime completedAt) {
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.completed = completedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public LocalTime getTimeStart() {
        return timeStart;
    }

    public LocalTime getTimeEnd() {
        return timeEnd;
    }

    public String getRegion() {
        return region;
    }

    public String getStudio() {
        return studio;
    }

    public String getRoom() {
        return room;
    }

    public String getGenre() {
        return genre;
    }

    public String getFee() {
        return fee;
    }

    public String getDescription() {
        return description;
    }

    public String getAfterparty() {
        return afterparty;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMaxSongsPerUser() {
        return maxSongsPerUser;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Set<Part> getDefaultParts() {
        return defaultParts;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public Member getHost() {
        return host;
    }

    public boolean isCompleted() {
        return completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public void setTimeStart(LocalTime timeStart) {
        this.timeStart = timeStart;
    }

    public void setTimeEnd(LocalTime timeEnd) {
        this.timeEnd = timeEnd;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setStudio(String studio) {
        this.studio = studio;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAfterparty(String afterparty) {
        this.afterparty = afterparty;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setMaxSongsPerUser(int maxSongsPerUser) {
        this.maxSongsPerUser = maxSongsPerUser;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }
}