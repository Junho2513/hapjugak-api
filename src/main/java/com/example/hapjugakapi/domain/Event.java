package com.example.hapjugakapi.domain;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
public class Event {

    public enum Visibility { PUBLIC, PRIVATE }

    private static final SecureRandom KEY_RANDOM = new SecureRandom();

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

    /**
     * 참가비를 둘로 나눠 둔 이유: 한 칸이던 시절에는 "15,000", "만오천",
     * "대관비 N빵" 이 한 컬럼에 섞여 들어와 정렬도 필터도 할 수 없었다.
     * 숫자는 숫자로, 사람에게 하는 말은 말로 둔다.
     */
    private Integer feeAmount;

    @Column(length = 200)
    private String feeNote;

    @Column(length = 2000)
    private String description;

    @Column(length = 40)
    private String afterparty;

    /** 연습실이 몇 명 들어가는 방인지. 안내값이고 자리를 막지는 않는다. */
    private int roomCapacity;

    private int maxSongsPerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility = Visibility.PUBLIC;

    /**
     * 추측할 수 없는 초대 키. 비공개 일정의 주소에 붙는다.
     *
     * 공개 일정에도 똑같이 만들어 둔다. 나중에 비공개로 바꾸는 순간
     * 키가 없으면 그때 만들어야 하는데, 그러면 "키가 없는 일정"이라는 상태를
     * 코드 곳곳에서 신경 써야 한다. 항상 있는 편이 단순하다.
     */
    @Column(nullable = false, length = 16, unique = true)
    private String inviteKey = newInviteKey();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_default_part",
            joinColumns = @JoinColumn(name = "event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "part", length = 20)
    private Set<Part> defaultParts = new LinkedHashSet<>();

    /** S3에 올라간 이미지의 공개 주소. 이미지 자체는 DB에 두지 않는다. */
    @Column(length = 500)
    private String coverUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host;

    private boolean completed = false;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Event() {
    }

    /**
     * 무작위 12바이트를 주소에 쓸 수 있는 16글자로 옮긴다.
     *
     * SecureRandom 을 쓴다. Math.random 이나 UUID.randomUUID 의 일부를 잘라 쓰면
     * 앞 사람의 키를 보고 다음 키를 좁힐 여지가 생긴다. 막으려는 것이 바로 그것이다.
     */
    private static String newInviteKey() {
        byte[] bytes = new byte[12];
        KEY_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 공개였다가 비공개로 바뀌면 키를 새로 뽑는다. 공개 시절의 주소는 이미 퍼졌을 수 있다. */
    public void setVisibility(Visibility visibility) {
        if (visibility == Visibility.PRIVATE && this.visibility != Visibility.PRIVATE) {
            this.inviteKey = newInviteKey();
        }
        this.visibility = visibility;
    }

    /** 이 사람이 이 일정을 열어도 되는지. 공개 일정은 누구나 볼 수 있다. */
    public boolean isOpenWith(String key) {
        if (visibility != Visibility.PRIVATE) {
            return true;
        }
        if (key == null) {
            return false;
        }
        // 글자를 하나씩 비교하다 다른 곳에서 멈추면, 걸린 시간으로 몇 글자가 맞았는지 샌다.
        return MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                inviteKey.getBytes(StandardCharsets.UTF_8));
    }

    public String getInviteKey() {
        return inviteKey;
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

    /** 완료 표시를 해제하고 다시 편집할 수 있게 되돌린다. */
    public void reopen() {
        this.completed = false;
        this.completedAt = null;
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

    public Integer getFeeAmount() {
        return feeAmount;
    }

    public String getFeeNote() {
        return feeNote;
    }

    public String getDescription() {
        return description;
    }

    public String getAfterparty() {
        return afterparty;
    }

    public int getRoomCapacity() {
        return roomCapacity;
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

    public String getCoverUrl() {
        return coverUrl;
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

    public void setFeeAmount(Integer feeAmount) {
        this.feeAmount = feeAmount;
    }

    public void setFeeNote(String feeNote) {
        this.feeNote = feeNote;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAfterparty(String afterparty) {
        this.afterparty = afterparty;
    }

    public void setRoomCapacity(int roomCapacity) {
        this.roomCapacity = roomCapacity;
    }

    public void setMaxSongsPerUser(int maxSongsPerUser) {
        this.maxSongsPerUser = maxSongsPerUser;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
}