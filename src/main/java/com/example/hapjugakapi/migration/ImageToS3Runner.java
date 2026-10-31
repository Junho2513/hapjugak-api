package com.example.hapjugakapi.migration;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.SiteSettings;
import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.repository.EventRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SiteSettingsRepository;
import com.example.hapjugakapi.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * DB 안에 base64로 들어 있던 이미지를 S3로 옮긴다.
 *
 *   docker compose run --rm -e SPRING_PROFILES_ACTIVE=s3migrate api
 *
 * 안전장치
 *  - @Profile("s3migrate") — 평소 실행에는 빈으로 등록조차 되지 않는다
 *  - 이미 옮긴 대상(URL이 있는)은 건너뛴다. 두 번 돌려도 중복되지 않는다
 *  - 한 건씩 저장한다. 중간에 끊겨도 거기까지는 남고, 다시 돌리면 이어서 한다
 *
 * 전체를 하나의 트랜잭션으로 묶지 않은 것은 의도다.
 * S3 업로드는 롤백되지 않기 때문에, DB만 되돌리면 올라간 파일이 고아로 남는다.
 * 한 건씩 커밋하면 "DB와 S3가 어긋난 상태"가 한 건을 넘지 않는다.
 */
@Component
@Profile("s3migrate")
public class ImageToS3Runner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageToS3Runner.class);

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final SiteSettingsRepository siteSettingsRepository;
    private final StorageService storageService;

    public ImageToS3Runner(MemberRepository memberRepository,
                           EventRepository eventRepository,
                           SiteSettingsRepository siteSettingsRepository,
                           StorageService storageService) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.siteSettingsRepository = siteSettingsRepository;
        this.storageService = storageService;
    }

    @Override
    public void run(String... args) {
        moveMembers();
        moveEvents();
        moveSettings();
    }

    private void moveMembers() {
        List<Member> all = memberRepository.findAll();
        int moved = 0, skipped = 0, failed = 0;
        log.info("[S3 이관] 프로필 — 회원 {}명 확인 시작", all.size());

        for (Member member : all) {
            if (member.getPhotoUrl() != null || isBlank(member.getPhotoImage())) {
                skipped++;
                continue;
            }
            try {
                byte[] bytes = decode(member.getPhotoImage());
                String type = sniff(bytes);
                if (type == null) { failed++; warnUnknown("회원", member.getId()); continue; }
                String url = storageService.uploadBytes(
                        UploadKind.PROFILE, bytes, type, "member-" + member.getId());
                member.setPhotoUrl(url);
                memberRepository.save(member);
                moved++;
                log.info("[S3 이관] 프로필 {}번 {} — {}KB → {}",
                        member.getId(), member.getNickname(), bytes.length / 1024, url);
            } catch (RuntimeException e) {
                failed++;
                log.error("[S3 이관] 프로필 {}번 실패: {}", member.getId(), e.getMessage());
            }
        }
        log.info("[S3 이관] 프로필 완료 — 옮김 {}건, 건너뜀 {}건, 실패 {}건", moved, skipped, failed);
    }

    private void moveEvents() {
        List<Event> all = eventRepository.findAll();
        int moved = 0, skipped = 0, failed = 0;
        log.info("[S3 이관] 커버 — 일정 {}건 확인 시작", all.size());

        for (Event event : all) {
            if (event.getCoverUrl() != null || isBlank(event.getCoverImage())) {
                skipped++;
                continue;
            }
            try {
                byte[] bytes = decode(event.getCoverImage());
                String type = sniff(bytes);
                if (type == null) { failed++; warnUnknown("일정", event.getId()); continue; }
                String url = storageService.uploadBytes(
                        UploadKind.COVER, bytes, type, "event-" + event.getId());
                event.setCoverUrl(url);
                eventRepository.save(event);
                moved++;
                log.info("[S3 이관] 커버 {}번 {} — {}KB → {}",
                        event.getId(), event.getTitle(), bytes.length / 1024, url);
            } catch (RuntimeException e) {
                failed++;
                log.error("[S3 이관] 커버 {}번 실패: {}", event.getId(), e.getMessage());
            }
        }
        log.info("[S3 이관] 커버 완료 — 옮김 {}건, 건너뜀 {}건, 실패 {}건", moved, skipped, failed);
    }

    private void moveSettings() {
        SiteSettings settings = siteSettingsRepository
                .findById(SiteSettings.SINGLETON_ID).orElse(null);
        if (settings == null || settings.getHeroUrl() != null || isBlank(settings.getHeroImage())) {
            log.info("[S3 이관] 배너 — 옮길 것 없음");
            return;
        }
        try {
            byte[] bytes = decode(settings.getHeroImage());
            String type = sniff(bytes);
            if (type == null) { warnUnknown("배너", SiteSettings.SINGLETON_ID); return; }
            String url = storageService.uploadBytes(UploadKind.HERO, bytes, type, "hero");
            settings.setHeroUrl(url);
            siteSettingsRepository.save(settings);
            log.info("[S3 이관] 배너 — {}KB → {}", bytes.length / 1024, url);
        } catch (RuntimeException e) {
            log.error("[S3 이관] 배너 실패: {}", e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void warnUnknown(String what, Long id) {
        log.warn("[S3 이관] {} {}번 — 이미지 형식을 알 수 없어 건너뜁니다", what, id);
    }

    /**
     * 저장돼 있던 값은 두 가지 모양 중 하나다.
     *   data:image/png;base64,iVBORw0KG...   (대부분)
     *   iVBORw0KG...                         (접두사 없이)
     */
    private static byte[] decode(String raw) {
        String data = raw;
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) {
            data = data.substring(comma + 1);
        }
        data = data.replaceAll("\\s", "");
        return Base64.getDecoder().decode(data);
    }

    /**
     * 앞머리 몇 바이트로 실제 형식을 알아낸다.
     *
     * data: 접두사에 적힌 타입을 믿지 않는 이유 — 그 문자열은 옛 프론트가 적어 넣은 값이라
     * 실제 내용과 다를 수 있다. 바이트는 거짓말을 하지 않는다.
     */
    private static String sniff(byte[] b) {
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
