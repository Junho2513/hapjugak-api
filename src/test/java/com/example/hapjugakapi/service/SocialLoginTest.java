package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.SocialClient;
import com.example.hapjugakapi.config.SocialCode;
import com.example.hapjugakapi.config.SocialUser;
import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.SocialAccount;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SocialAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.hapjugakapi.config.JwtTokenProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「이 소셜 계정은 우리 쪽 누구인가」.
 *
 * <p>전에는 한 줄이었다. 소셜이 말하는 이메일로 기존 계정을 찾아 그대로
 * 로그인시켰다. 구글만 있을 때는 대체로 괜찮았다 — 구글은 이메일을 확인하고 준다.
 *
 * <p>카카오가 붙으면 그게 구멍이 된다. 카카오계정 이메일은 미인증일 수 있다.
 * 자기 카카오 계정의 이메일을 남의 주소로 바꾸고 로그인하면 그 사람이 되는 것이다.
 *
 * <p>여기서 확인하는 것은 그 구멍이 실제로 막혔는지다.
 *
 * <p>제공자를 진짜로 부르지 않는다. 카카오·네이버를 호출하는 테스트는
 * 그쪽 사정에 따라 깨지고, 확인하려는 것은 "카카오가 응답을 주더라"가 아니라
 * "그 응답을 받고 우리가 어떻게 판단하는가"다. 그래서 응답을 손으로 만들어 넣는다.
 */
@SpringBootTest
@Transactional
class SocialLoginTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MemberService memberService;

    private static final SocialCode 아무코드 = new SocialCode("code", "state");

    /** 제공자가 이렇게 답했다고 치자 — 테스트마다 여기에 넣는다. */
    private final Map<AuthProvider, SocialUser> 답 = new EnumMap<>(AuthProvider.class);

    private AuthService authService;

    /**
     * AuthService 를 손으로 조립한다.
     *
     * 스프링이 만들어준 것을 쓰면 진짜 카카오·네이버 클라이언트가 들어가고,
     * 그러면 테스트가 바깥 서비스를 부르게 된다. 가짜 빈으로 덮어쓰는 방법도 있지만
     * 같은 이름의 빈을 두 번 정의하는 것이라 Boot 가 막는다.
     *
     * 여기서 확인하려는 것은 "카카오가 응답을 주더라"가 아니라
     * "그 응답을 받고 우리가 누구로 판단하는가"다. 그 판단은 AuthService 안에 있고,
     * 저장소는 진짜를 쓰므로 DB 제약과 조회 규칙도 그대로 걸린다.
     *
     * @Transactional 이 붙은 테스트 메서드 안에서 부르므로, 직접 만든 객체라
     * 프록시가 없어도 저장소 호출이 같은 트랜잭션에 참여한다. 끝나면 되돌려진다.
     */
    @BeforeEach
    void 준비() {
        답.clear();
        List<SocialClient> 가짜들 = new ArrayList<>();
        for (AuthProvider p : List.of(AuthProvider.GOOGLE, AuthProvider.KAKAO, AuthProvider.NAVER)) {
            가짜들.add(가짜(p));
        }
        authService = new AuthService(memberRepository, socialAccountRepository,
                passwordEncoder, jwtTokenProvider, 가짜들);
    }

    private SocialClient 가짜(AuthProvider provider) {
        return new SocialClient() {
            @Override
            public AuthProvider provider() {
                return provider;
            }

            @Override
            public SocialUser exchange(SocialCode code) {
                SocialUser answer = 답.get(provider);
                if (answer == null) {
                    throw new UnauthorizedException("테스트가 답을 준비하지 않았습니다.");
                }
                return answer;
            }
        };
    }

    private void 제공자가답한다(AuthProvider provider, SocialUser user) {
        답.put(provider, user);
    }

    private Member 로그인(AuthProvider provider) {
        TokenResponse issued = authService.socialLogin(provider, 아무코드);
        return memberRepository.findById(issued.memberId()).orElseThrow();
    }

    // ── 막아야 하는 것 ──────────────────────────────────────────────

    @Test
    @DisplayName("미인증 이메일로는 남의 계정에 들어갈 수 없다")
    void 미인증_이메일로는_못_들어간다() {
        // 피해자는 자체 가입했다. 비밀번호를 알아야 들어갈 수 있어야 한다.
        memberRepository.save(new Member("victim@test.com", "{noop}pw", "피해자", "서울", ""));

        // 공격자가 자기 카카오 계정의 이메일을 피해자 주소로 바꿨다.
        // 카카오는 그 주소를 확인해 주지 않는다(is_email_verified: false).
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-attacker", "victim@test.com", false, "공격자"));

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.KAKAO, 아무코드));
        assertTrue(e.getMessage().contains("가입된 계정이 있습니다"), e.getMessage());
        assertTrue(e.getMessage().contains("비밀번호"), e.getMessage());
    }

    @Test
    @DisplayName("막은 뒤에도 피해자 계정은 건드려지지 않는다")
    void 막은_뒤_계정은_그대로다() {
        Member 피해자 = memberRepository.save(
                new Member("victim2@test.com", "{noop}pw", "피해자", "서울", ""));
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-attacker", "victim2@test.com", false, "공격자"));

        assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.KAKAO, 아무코드));

        assertTrue(연결(피해자).isEmpty(), "연결이 생기면 안 된다");
        assertEquals("피해자", memberRepository.findById(피해자.getId()).orElseThrow().getNickname());
    }

    @Test
    @DisplayName("네이버 이메일은 인증 여부를 알 수 없으므로 자동으로 잇지 않는다")
    void 네이버는_자동으로_잇지_않는다() {
        memberRepository.save(new Member("both@test.com", "{noop}pw", "먼저온사람", "서울", ""));
        제공자가답한다(AuthProvider.NAVER,
                new SocialUser("naver-1", "both@test.com", false, "같은사람일수도"));

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.NAVER, 아무코드));
        // 막기만 하면 길이 없다. 어떻게 하면 되는지 알려줘야 한다.
        assertTrue(e.getMessage().contains("연결"), e.getMessage());
    }

    // ── 되어야 하는 것 ──────────────────────────────────────────────

    @Test
    @DisplayName("확인된 이메일이면 기존 계정에 이어붙인다")
    void 확인된_이메일은_잇는다() {
        Member 본인 = memberRepository.save(
                new Member("me@test.com", "{noop}pw", "본인", "서울", ""));
        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-1", "me@test.com", true, "본인"));

        Member 들어온사람 = 로그인(AuthProvider.GOOGLE);

        assertEquals(본인.getId(), 들어온사람.getId(), "새 계정이 생기면 안 된다");
        assertEquals(List.of(AuthProvider.GOOGLE), 연결(본인));
    }

    @Test
    @DisplayName("처음 보는 사람이면 계정을 만든다")
    void 처음이면_만든다() {
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-new", "new@test.com", true, "새사람"));

        Member 만들어진사람 = 로그인(AuthProvider.KAKAO);

        assertEquals("new@test.com", 만들어진사람.getEmail());
        assertEquals("새사람", 만들어진사람.getNickname());
        assertEquals(List.of(AuthProvider.KAKAO), 연결(만들어진사람));
    }

    @Test
    @DisplayName("이메일을 안 줘도 가입된다 — 카카오·네이버는 선택 동의다")
    void 이메일이_없어도_된다() {
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-noemail", null, false, "메일안준사람"));

        Member 만들어진사람 = 로그인(AuthProvider.KAKAO);

        assertNull(만들어진사람.getEmail());
        assertEquals(List.of(AuthProvider.KAKAO), 연결(만들어진사람));
    }

    @Test
    @DisplayName("두 번째부터는 고유번호로 찾는다 — 이메일이 바뀌어도 같은 사람이다")
    void 두_번째는_고유번호로() {
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-same", "before@test.com", true, "그사람"));
        Long 처음 = 로그인(AuthProvider.KAKAO).getId();

        // 그쪽에서 이메일을 바꿨다. 우리 입장에서는 여전히 같은 사람이다.
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-same", "after@test.com", true, "그사람"));
        Long 두번째 = 로그인(AuthProvider.KAKAO).getId();

        assertEquals(처음, 두번째, "같은 소셜 계정이면 같은 회원이어야 한다");
    }

    @Test
    @DisplayName("제공자가 다르면 고유번호가 같아도 다른 사람이다")
    void 제공자가_다르면_다른_사람() {
        제공자가답한다(AuthProvider.KAKAO, new SocialUser("12345", null, false, "카카오사람"));
        제공자가답한다(AuthProvider.NAVER, new SocialUser("12345", null, false, "네이버사람"));

        Long 카카오 = 로그인(AuthProvider.KAKAO).getId();
        Long 네이버 = 로그인(AuthProvider.NAVER).getId();

        assertNotEquals(카카오, 네이버, "번호가 겹친다고 같은 사람으로 보면 안 된다");
    }

    @Test
    @DisplayName("같은 제공자인데 고유번호가 낡았으면 고쳐서 들여보낸다")
    void 낡은_고유번호를_고친다() {
        // Firestore 에서 옮겨온 계정들이 이 모습이었다. provider 는 GOOGLE 인데
        // 연결에 들어 있던 것이 구글의 sub 가 아니라 Firebase UID 였다.
        // 그때는 이메일로 사람을 찾아서 아무도 몰랐고, 기준을 고유번호로 바꾸자
        // 27명 전원이 로그인하지 못하게 됐다.
        Member 옮겨온계정 = memberRepository.save(
                Member.social(AuthProvider.GOOGLE, "moved@test.com", "옮겨온사람", "firebase-uid-옛것"));
        socialAccountRepository.save(
                new SocialAccount(옮겨온계정, AuthProvider.GOOGLE, "firebase-uid-옛것"));

        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-sub-진짜", "moved@test.com", true, "옮겨온사람"));

        Member 들어온사람 = 로그인(AuthProvider.GOOGLE);

        assertEquals(옮겨온계정.getId(), 들어온사람.getId(), "새 계정이 생기면 안 된다");
        assertEquals(1, 연결(옮겨온계정).size(), "연결이 늘어나면 안 된다 — 고쳐져야 한다");
        assertEquals("google-sub-진짜",
                socialAccountRepository
                        .findByMemberIdAndProvider(옮겨온계정.getId(), AuthProvider.GOOGLE)
                        .orElseThrow().getProviderId());

        // 한 번 고쳐졌으니 다음부터는 ①에서 바로 찾아진다.
        assertEquals(옮겨온계정.getId(), 로그인(AuthProvider.GOOGLE).getId());
    }

    // ── 한 사람이 여러 제공자를 쓴다 ────────────────────────────────

    @Test
    @DisplayName("확인된 이메일이 같으면 제공자가 달라도 한 계정으로 모인다")
    void 두_제공자가_한_계정으로() {
        // 카카오에 네이버 주소를 적어두는 사람이 많다. 실제로 겪은 경우다.
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-9", "same@test.com", true, "한사람"));
        Member 처음 = 로그인(AuthProvider.KAKAO);

        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-9", "same@test.com", true, "한사람"));
        Member 두번째 = 로그인(AuthProvider.GOOGLE);

        assertEquals(처음.getId(), 두번째.getId(), "계정이 둘로 갈라지면 안 된다");
        assertEquals(List.of(AuthProvider.GOOGLE, AuthProvider.KAKAO), 연결(처음),
                "둘 다 들고 있어야 한다");

        // 먼저 쓰던 카카오도 그대로 통해야 한다. 덮어쓰면 여기서 걸린다.
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-9", "same@test.com", true, "한사람"));
        assertEquals(처음.getId(), 로그인(AuthProvider.KAKAO).getId());
    }

    @Test
    @DisplayName("로그인한 상태에서는 네이버도 직접 이을 수 있다")
    void 로그인한_뒤_네이버를_잇는다() {
        // 네이버는 이메일 인증 여부를 알려주지 않아 자동으로는 못 잇는다.
        // 그런데 이미 로그인한 사람이라면 본인임이 증명돼 있다.
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-me", "me2@test.com", true, "나"));
        Member 나 = 로그인(AuthProvider.KAKAO);

        제공자가답한다(AuthProvider.NAVER,
                new SocialUser("naver-me", "me2@test.com", false, "나"));
        authService.linkTo(나.getId(), AuthProvider.NAVER, 아무코드);

        assertEquals(List.of(AuthProvider.KAKAO, AuthProvider.NAVER), 연결(나));

        // 이제 네이버로도 들어와진다.
        assertEquals(나.getId(), 로그인(AuthProvider.NAVER).getId());
    }

    @Test
    @DisplayName("남의 계정에 붙어 있는 소셜 계정은 가져올 수 없다")
    void 남의_연결은_못_가져온다() {
        제공자가답한다(AuthProvider.KAKAO, new SocialUser("kakao-a", null, false, "먼저"));
        Member 먼저 = 로그인(AuthProvider.KAKAO);

        제공자가답한다(AuthProvider.GOOGLE, new SocialUser("google-b", null, false, "나중"));
        Member 나중 = 로그인(AuthProvider.GOOGLE);

        // 나중이 먼저의 카카오 계정을 자기 것으로 붙이려 한다.
        assertThrows(ConflictException.class,
                () -> authService.linkTo(나중.getId(), AuthProvider.KAKAO, 아무코드));

        assertEquals(List.of(AuthProvider.KAKAO), 연결(먼저), "먼저의 연결이 끊기면 안 된다");
    }

    @Test
    @DisplayName("마지막 로그인 방법은 끊을 수 없다")
    void 마지막_길은_못_끊는다() {
        제공자가답한다(AuthProvider.GOOGLE, new SocialUser("google-only", null, false, "하나뿐"));
        Member 나 = 로그인(AuthProvider.GOOGLE);

        ConflictException e = assertThrows(ConflictException.class,
                () -> authService.unlink(나.getId(), AuthProvider.GOOGLE));
        assertTrue(e.getMessage().contains("마지막"), e.getMessage());
        assertEquals(List.of(AuthProvider.GOOGLE), 연결(나), "끊기면 안 된다");
    }

    @Test
    @DisplayName("둘 이상이면 하나는 끊을 수 있다")
    void 둘이면_하나는_끊는다() {
        제공자가답한다(AuthProvider.KAKAO, new SocialUser("k", "two@test.com", true, "둘가진사람"));
        Member 나 = 로그인(AuthProvider.KAKAO);
        제공자가답한다(AuthProvider.NAVER, new SocialUser("n", null, false, "둘가진사람"));
        authService.linkTo(나.getId(), AuthProvider.NAVER, 아무코드);

        authService.unlink(나.getId(), AuthProvider.NAVER);

        assertEquals(List.of(AuthProvider.KAKAO), 연결(나));
    }

    @Test
    @DisplayName("비밀번호가 있으면 하나뿐인 소셜도 끊을 수 있다")
    void 비번이_있으면_끊는다() {
        Member 나 = memberRepository.save(
                new Member("pw@test.com", "{noop}pw", "비번있는사람", "서울", ""));
        제공자가답한다(AuthProvider.GOOGLE, new SocialUser("g", "pw@test.com", true, "비번있는사람"));
        로그인(AuthProvider.GOOGLE);

        authService.unlink(나.getId(), AuthProvider.GOOGLE);

        assertTrue(연결(나).isEmpty());
    }

    // ── 탈퇴 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("탈퇴하면 연결도 지워져 같은 소셜 계정으로 다시 가입된다")
    void 탈퇴하면_연결도_지운다() {
        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-back", "back@test.com", true, "돌아올사람"));
        Long 옛id = 로그인(AuthProvider.GOOGLE).getId();

        memberService.withdraw(옛id, 옛id);

        Long 새id = 로그인(AuthProvider.GOOGLE).getId();

        assertNotEquals(옛id, 새id, "탈퇴한 행을 되살리면 안 된다");
        assertTrue(socialAccountRepository.findByMemberIdOrderByProviderAsc(옛id).isEmpty(),
                "연결이 남으면 (provider, provider_id) 유니크에 걸려 재가입이 막힌다");
    }

    @Test
    @DisplayName("자체 로그인 길로는 소셜 제공자를 부를 수 없다")
    void 로컬은_소셜이_아니다() {
        assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.LOCAL, 아무코드));
    }

    // ── 거들기 ──────────────────────────────────────────────────────

    private List<AuthProvider> 연결(Member member) {
        return socialAccountRepository.findByMemberIdOrderByProviderAsc(member.getId()).stream()
                .map(com.example.hapjugakapi.domain.SocialAccount::getProvider)
                .toList();
    }
}
