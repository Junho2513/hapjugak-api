package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Member actor;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 20)
    private String actorName;

    @Column(length = 100)
    private String songTitle;

    /**
     * 어느 자리에 대한 기록인지. 이제 종류가 아니라 <b>이름</b>을 적는다.
     * 직접 적은 파트(「색소폰」)도 기록에 남아야 하고,
     * 나중에 그 자리가 사라져도 기록의 이름은 그대로 남아야 한다.
     */
    @Column(length = 30)
    private String part;

    @Column(length = 200)
    private String detail;

    @Column(length = 20)
    private String targetName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ActivityLog() {
    }

    public ActivityLog(Song song, Member actor, String type) {
        this.song = song;
        this.actor = actor;
        this.type = type;
        this.actorName = (actor != null) ? actor.getNickname() : null;
        this.songTitle = song.getTitle();
    }

    public void describe(String part, String detail, String targetName) {
        this.part = part;
        this.detail = detail;
        this.targetName = targetName;
    }

    /** Firestore 이관 전용. 당시의 이름과 시각을 그대로 복원한다. */
    public void restore(String actorName, String songTitle, LocalDateTime createdAt) {
        this.actorName = actorName;
        this.songTitle = songTitle;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Song getSong() {
        return song;
    }

    public Member getActor() {
        return actor;
    }

    public String getType() {
        return type;
    }

    public String getActorName() {
        return actorName;
    }

    public String getSongTitle() {
        return songTitle;
    }

    public String getPart() {
        return part;
    }

    public String getDetail() {
        return detail;
    }

    public String getTargetName() {
        return targetName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}