package com.example.hapjugakapi.migration;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.repository.MemberRepository;
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
 *   ./gradlew bootRun --args='--spring.profiles.active=s3migrate'
 *
 * 안전장치
 *  - @Profile("s3migrate") — 평소 실행에는 빈으로 등록조차 되지 않는다
 *  - 이미 옮긴 회원(photoUrl이 있는)은 건너뛴다. 두 번 돌려도 중복되지 않는다
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
    private final StorageService storageService;

    public ImageToS3Runner(MemberRepository memberRepository, StorageService storageService) {
        this.memberRepository = memberRepository;
        this.storageService = storageService;
    }

    @Override
    public void run(String... args) {
        List<Member> members = memberRepository.findAll();
        int moved = 0, skipped = 0, failed = 0;

        log.info("[S3 이관] 회원 {}명 확인 시작", members.size());

        for (Member member : members) {
            if (member.getPhotoUrl() != null) {
                skipped++;
                continue;
            }
            String raw = member.getPhotoImage();
            if (raw == null || raw.isBlank()) {
                skipped++;
                continue;
            }
            try {
                byte[] bytes = decode(raw);
                String contentType = sniff(bytes);
                if (contentType == null) {
                    log.warn("[S3 이관] {}번 회원 — 이미지 형식을 알 수 없어 건너뜁니다", member.getId());
                    failed++;
                    continue;
                }
                String url = storageService.uploadBytes(
                        UploadKind.PROFILE, bytes, contentType, "member-" + member.getId());
                member.setPhotoUrl(url);
                memberRepository.save(member);
                moved++;
                log.info("[S3 이관] {}번 {} — {}KB → {}",
                        member.getId(), member.getNickname(), bytes.length / 1024, url);
            } catch (RuntimeException e) {
                failed++;
                log.error("[S3 이관] {}번 회원 실패: {}", member.getId(), e.getMessage());
            }
        }

        log.info("[S3 이관] 완료 — 옮김 {}건, 건너뜀 {}건, 실패 {}건", moved, skipped, failed);
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
