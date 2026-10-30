package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String title;

    private LocalDate date;

    private LocalDate deadline;

    private String place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    private boolean completed = false;

    protected Event() {}

    public Event(String title, LocalDate date, LocalDate deadline, String place, Member host) {
        this.title = title;
        this.date = date;
        this.deadline = deadline;
        this.place = place;
        this.host = host;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public LocalDate getDeadline() { return deadline; }
    public String getPlace() { return place; }
    public Member getHost() { return host; }
    public boolean isCompleted() { return completed; }

    public void complete() {
        this.completed = true;
    }

    public boolean isDeadlinePassed() {
        return deadline != null && LocalDate.now().isAfter(deadline);
    }

    public boolean isHostedBy(Member member) {
        return host.getId().equals(member.getId());
    }
}