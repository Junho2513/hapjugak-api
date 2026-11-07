package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Instrument;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.Slot;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.domain.UploadKind;
import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberPublicResponse;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SlotRepository;
import com.example.hapjugakapi.repository.SocialAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final SlotRepository slotRepository;
    private final ActivityLogRepository activityLogRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    public MemberService(MemberRepository memberRepository,
                         SlotRepository slotRepository,
                         ActivityLogRepository activityLogRepository,
                         SocialAccountRepository socialAccountRepository,
                         PasswordEncoder passwordEncoder,
                         StorageService storageService) {
        this.memberRepository = memberRepository;
        this.slotRepository = slotRepository;
        this.activityLogRepository = activityLogRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        Member member = new Member(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.area(),
                request.bio()
        );
        applyProfile(member, request);
        return MemberResponse.from(memberRepository.save(member));
    }

    /** 탈퇴한 회원은 목록에 나오지 않는다. 행은 남아 있지만 사람은 없다. */
    public List<MemberResponse> findAll() {
        return memberRepository.findByDeletedAtIsNullOrderByIdAsc().stream()
                .map(MemberResponse::from)
                .toList();
    }

    /** 본인 또는 관리자만 본다. 이메일·역할이 들어 있다. */
    public MemberResponse findById(Long id) {
        return MemberResponse.from(getMember(id));
    }

    /**
     * 남이 보는 프로필. 이메일과 역할이 빠진다.
     *
     * 돌려주는 타입 자체를 다르게 둔 것이 요점이다.
     * 같은 MemberResponse 를 쓰면서 "여기선 이메일을 지우자"로 처리하면
     * 지우는 걸 한 번 잊는 순간 그대로 새어 나간다.
     */
    public MemberPublicResponse findPublicById(Long id) {
        return MemberPublicResponse.from(getMember(id));
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        Member member = getMember(id);
        if (request.nickname() != null) member.setNickname(request.nickname());
        if (request.area() != null) member.setArea(request.area());
        if (request.bio() != null) member.setBio(request.bio());
        applyProfile(member, request);
        return MemberResponse.from(member);
    }

    /**
     * 탈퇴. 본인 또는 관리자가 할 수 있다.
     *
     * 전에는 memberRepository.delete() 한 줄이었고, 관리자만 부를 수 있었다.
     * 두 가지가 잘못돼 있었다.
     *
     * 하나 — 본인이 나갈 길이 없었다. Swagger 설명에는 「본인 또는 관리자」라고
     * 적혀 있었는데 코드는 관리자만 통과시켰다. 남의 이메일을 들고 운영하는
     * 서비스에서 탈퇴는 기능이기 전에 의무에 가깝다.
     *
     * 둘 — 그 한 줄은 거의 항상 터졌다. member.id 를 가리키는 외래키가 네 군데
     * (event.host_id, song.proposer_id, slot.member_id, activity_log.actor_id)
     * 인데 ON DELETE 규칙이 없다. 합주를 열었거나 자리를 잡아본 사람은 지워지지 않고
     * 500 이 났다. 실사용자 중 이 조건을 피하는 사람은 거의 없었다.
     *
     * 그래서 지우지 않고 비운다. 하는 일은 넷이다.
     *   1. 잡고 있던 자리를 비우고, 그 곡의 성립 여부를 다시 센다
     *   2. 활동 기록에 남은 이름 사본을 덮는다
     *   3. 회원 행에서 사람을 지운다 (Member.withdraw)
     *   4. S3 의 프로필 사진 파일을 지운다
     *
     * 1번을 하는 이유 — 나간 사람이 드럼 자리를 계속 잡고 있으면 그 곡은
     * 영영 사람을 못 채운다. 이미 성립된 곡은 성립이 풀린다. 아쉽지만
     * 실제로 그 사람이 오지 않으므로 그게 사실에 맞다.
     *
     * 지우는 순서가 중요하다. 3번을 먼저 하면 2번이 쓸 닉네임이 이미 사라진다.
     */
    @Transactional
    public void withdraw(Long targetId, Long actorId) {
        Member actor = getMember(actorId);
        boolean self = targetId.equals(actorId);
        if (!self && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("본인 또는 관리자만 탈퇴시킬 수 있습니다.");
        }

        Member target = getMember(targetId);
        if (target.isWithdrawn()) {
            throw new ConflictException("이미 탈퇴한 회원입니다.");
        }

        releaseSlotsOf(target);
        scrubNameFromLogs(target.getId(), target.getNickname());

        // 소셜 연결도 지운다. 남겨두면 (provider, provider_id) 유니크에 걸려
        // 그 소셜 계정으로는 다시 가입할 수 없다. 나간 사람이 돌아올 길을
        // 막아두는 셈이 된다.
        socialAccountRepository.deleteByMemberId(target.getId());

        /*
         * 여기서 회원을 다시 읽는 이유.
         *
         * 위의 기록 덮어쓰기는 벌크 UPDATE 다. 영속성 컨텍스트를 지나쳐 DB 만 건드리므로,
         * 끝난 뒤 메모리에 들고 있던 엔티티는 DB 와 어긋난 상태가 된다.
         * 그래서 clearAutomatically 로 컨텍스트를 비운다.
         *
         * 비우는 순간 위에서 읽어둔 target 도 같이 준영속이 된다. 그 상태로
         * withdraw() 를 불러봐야 트랜잭션이 끝날 때 UPDATE 가 나가지 않는다.
         * 아무 예외도 없이 "탈퇴했는데 그대로 남아 있는" 결과가 된다.
         *
         * 순서를 바꿀 수도 없다. 기록을 덮으려면 아직 지우지 않은 닉네임이 필요하다.
         */
        Member reloaded = getMember(targetId);
        String photo = reloaded.withdraw(LocalDateTime.now());
        storageService.deleteByUrl(photo);
    }

    /** 잡고 있던 자리를 전부 비우고, 자리가 빠진 곡의 성립을 다시 센다. */
    private void releaseSlotsOf(Member target) {
        List<Slot> held = slotRepository.findByMemberId(target.getId());
        Set<Song> touched = new LinkedHashSet<>();
        for (Slot slot : held) {
            slot.release();
            touched.add(slot.getSong());
        }
        touched.forEach(Song::refreshCompletion);
    }

    /**
     * 활동 기록의 이름 사본을 덮는다.
     *
     * 기록 자체는 지우지 않는다. 남의 곡에 무슨 일이 있었는지가 거기 들어 있어서,
     * 한 사람이 나갔다고 통째로 지우면 남의 역사가 사라진다.
     */
    private void scrubNameFromLogs(Long memberId, String nickname) {
        activityLogRepository.anonymizeActorName(memberId, Member.WITHDRAWN_NICKNAME);
        activityLogRepository.anonymizeTargetName(nickname, Member.WITHDRAWN_NICKNAME);
    }

    private void applyProfile(Member member, MemberRequest request) {
        applyPhoto(member, request.photoUrl());
        if (request.instruments() != null) {
            /* 같은 파트를 공백·대소문자만 바꿔 여러 번 보내는 경우가 있다.
               그대로 저장하면 목록에 같은 이름이 두 번 뜨고, 왜 그런지 아무도 모른다.
               다듬은 값으로 중복을 걸러 내고, 처음 적은 표기를 남긴다. */
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            java.util.List<String> kept = new java.util.ArrayList<>();
            for (String raw : request.instruments()) {
                String v = Instrument.normalize(raw);
                if (v == null || !seen.add(Instrument.key(v))) continue;
                kept.add(v);
                if (kept.size() >= Instrument.MAX_COUNT) break;
            }
            member.getInstruments().clear();
            kept.forEach(member::addInstrument);
        }
    }

    /**
     * null  — 사진을 건드리지 않는다
     * ""    — 사진을 지운다
     * 그 외 — 업로드된 파일을 검증하고 주소를 저장한다
     *
     * 검증을 여기서 하는 이유 — 업로드 서명에는 파일 크기를 묶을 수 없다.
     * 브라우저가 서명을 받은 뒤 한도보다 큰 파일을 올려도 S3는 받아준다.
     * 그래서 "그 주소를 DB에 저장해도 되는지"를 이 시점에 서버가 다시 확인한다.
     */
    private void applyPhoto(Member member, String requested) {
        if (requested == null) return;

        String current = member.getPhotoUrl();
        if (requested.equals(current)) return;   // 안 바뀌었으면 S3를 부를 이유가 없다

        String verified = storageService.verify(requested, UploadKind.PROFILE);
        member.setPhotoUrl(verified);
        storageService.deleteByUrl(current);     // 교체되었으니 옛 파일은 지운다
    }

    private Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
    }
}
