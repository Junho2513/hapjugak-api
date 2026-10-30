package com.example.hapjugakapi.migration;

import com.example.hapjugakapi.domain.*;
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SongRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Firestore 백업(JSON)을 MySQL로 옮긴다.
 * spring.profiles.active=migrate 일 때만 동작하며, 회원이 한 명이라도 있으면 아무것도 하지 않는다.
 */
@Component
@Profile("migrate")
public class FirestoreMigrationRunner implements CommandLineRunner {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final SongRepository songRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String file;

    public FirestoreMigrationRunner(MemberRepository memberRepository,
                                    EventRepository eventRepository,
                                    SongRepository songRepository,
                                    ActivityLogRepository activityLogRepository,
                                    @Value("${migration.file:data/firestore-backup.json}") String file) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.songRepository = songRepository;
        this.activityLogRepository = activityLogRepository;
        this.file = file;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void run(String... args) throws Exception {
        if (memberRepository.count() > 0) {
            System.out.println("[이관] 이미 회원 데이터가 있습니다. 중단합니다.");
            return;
        }

        Map<String, Object> root = objectMapper.readValue(Files.readString(Path.of(file)), Map.class);
        System.out.println("[이관] 시작 — " + file);

        // 1) 회원
        Map<String, Member> memberByUid = new HashMap<>();
        for (Map<String, Object> u : list(root, "users")) {
            String email = str(u, "email");
            if (email == null) {
                System.out.println("[이관] 이메일 없는 회원 건너뜀: " + str(u, "name"));
                continue;
            }
            Member member = Member.google(email, str(u, "name"), str(u, "id"));
            member.setArea(str(u, "region"));
            member.setBio(str(u, "bio"));
            member.setPhotoImage(str(u, "photo"));
            for (String label : instrumentLabels(u)) {
                try {
                    member.addInstrument(Instrument.fromLabel(label));
                } catch (IllegalArgumentException e) {
                    System.out.println("[이관] 알 수 없는 악기 무시: " + label);
                }
            }
            LocalDateTime joined = millis(u, "joinedAt");
            if (joined != null) member.restoreCreatedAt(joined);
            memberByUid.put(str(u, "id"), memberRepository.save(member));
        }

        // 2) 합주 일정
        Map<String, Event> eventByKey = new HashMap<>();
        for (Map<String, Object> e : list(root, "events")) {
            Member host = memberByUid.get(str(e, "hostUid"));
            if (host == null) {
                System.out.println("[이관] 주최자를 찾지 못해 일정 건너뜀: " + str(e, "title"));
                continue;
            }
            Event event = new Event(str(e, "title"), date(e, "date"), date(e, "entryDeadline"),
                    str(e, "studio"), host);
            event.setRoom(str(e, "room"));
            event.setRegion(str(e, "region"));
            event.setGenre(str(e, "genre"));
            event.setFee(str(e, "fee"));
            event.setDescription(str(e, "description"));
            event.setAfterparty(str(e, "afterparty"));
            event.setTimeStart(time(e, "timeStart"));
            event.setTimeEnd(time(e, "timeEnd"));
            event.setCapacity(number(e, "capacity"));
            event.setMaxSongsPerUser(number(e, "maxSongsPerUser"));
            event.setCoverImage(str(e, "cover"));
            event.setVisibility("private".equalsIgnoreCase(str(e, "visibility"))
                    ? Event.Visibility.PRIVATE : Event.Visibility.PUBLIC);
            for (String p : strings(e, "defaultParts")) {
                part(p).ifPresent(event::addDefaultPart);
            }
            event.restoreTimestamps(orNow(millis(e, "createdAt")), millis(e, "completedAt"));
            eventByKey.put(str(e, "id"), eventRepository.save(event));
        }

        // 3) 곡 + 파트 자리
        int slotTotal = 0;
        int slotFilled = 0;
        Map<String, Song> songByKey = new HashMap<>();
        for (Map<String, Object> s : list(root, "songs")) {
            Member proposer = memberByUid.get(str(s, "proposerUid"));
            if (proposer == null) {
                System.out.println("[이관] 제안자를 찾지 못해 곡 건너뜀: " + str(s, "title"));
                continue;
            }
            Song song = new Song(str(s, "title"), str(s, "artist"), str(s, "youtube"),
                    str(s, "description"), proposer);

            String eventKey = str(s, "eventId");
            if (eventKey != null && eventByKey.containsKey(eventKey)) {
                song.assignTo(eventByKey.get(eventKey));
            }
            for (String p : strings(s, "requiredParts")) {
                part(p).ifPresent(song::addSlot);
            }
            LocalDateTime created = millis(s, "createdAt");
            if (created != null) song.restoreCreatedAt(created);

            for (Map.Entry<String, Object> entry : rawMap(s, "slots").entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> holderRaw)) continue;
                Optional<Part> found = part(entry.getKey());
                if (found.isEmpty()) continue;
                Part p = found.get();
                Member holder = memberByUid.get(String.valueOf(holderRaw.get("uid")));
                if (holder == null) continue;

                Slot slot = song.getSlots().stream()
                        .filter(x -> x.getPart() == p && !x.isTaken())
                        .findFirst()
                        .orElse(null);
                if (slot == null) {
                    song.addSlot(p);
                    slot = song.getSlots().get(song.getSlots().size() - 1);
                }
                slot.claimBy(holder);
                slotFilled++;
            }
            slotTotal += song.getSlots().size();
            songByKey.put(str(s, "id"), songRepository.save(song));
        }

        // 4) 활동 로그
        int logCount = 0;
        for (Map<String, Object> s : list(root, "songs")) {
            Song song = songByKey.get(str(s, "id"));
            if (song == null) continue;
            for (Map<String, Object> l : list(s, "__logs")) {
                String type = str(l, "type");
                if (type == null) continue;
                ActivityLog log = new ActivityLog(song, memberByUid.get(str(l, "actorUid")), type);
                log.describe(part(str(l, "part")).orElse(null), str(l, "detail"), str(l, "targetName"));
                log.restore(str(l, "actorName"), str(l, "songTitle"), orNow(millis(l, "ts")));
                activityLogRepository.save(log);
                logCount++;
            }
        }

        System.out.println("""
                ────────────── 이관 완료 ──────────────
                회원      %d명
                합주 일정  %d건
                곡        %d건
                파트 자리  %d개 (그중 %d개 채워짐)
                활동 로그  %d건
                ──────────────────────────────────────"""
                .formatted(memberByUid.size(), eventByKey.size(), songByKey.size(),
                        slotTotal, slotFilled, logCount));
    }

    // ───────── JSON 읽기 도우미 ─────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> node, String key) {
        Object value = node.get(key);
        return (value instanceof List<?> l) ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawMap(Map<String, Object> node, String key) {
        Object value = node.get(key);
        return (value instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
    }

    private static List<String> strings(Map<String, Object> node, String key) {
        List<String> result = new ArrayList<>();
        if (node.get(key) instanceof List<?> l) {
            for (Object o : l) if (o != null) result.add(String.valueOf(o));
        }
        return result;
    }

    private static List<String> instrumentLabels(Map<String, Object> user) {
        List<String> result = new ArrayList<>(strings(user, "parts"));
        String single = str(user, "part");
        if (single != null && !result.contains(single)) result.add(single);
        return result;
    }

    /** 빈 문자열은 null로 취급한다. Firestore에는 ""가 "값 없음"의 의미로 들어있다. */
    private static String str(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int number(Map<String, Object> node, String key) {
        return (node.get(key) instanceof Number n) ? n.intValue() : 0;
    }

    private static LocalDate date(Map<String, Object> node, String key) {
        String text = str(node, key);
        return text == null ? null : LocalDate.parse(text);
    }

    private static LocalTime time(Map<String, Object> node, String key) {
        String text = str(node, key);
        return text == null ? null : LocalTime.parse(text);
    }

    private static LocalDateTime millis(Map<String, Object> node, String key) {
        if (!(node.get(key) instanceof Number n)) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), SEOUL);
    }

    private static LocalDateTime orNow(LocalDateTime value) {
        return value != null ? value : LocalDateTime.now();
    }

    private static Optional<Part> part(String name) {
        if (name == null) return Optional.empty();
        try {
            return Optional.of(Part.valueOf(name.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}