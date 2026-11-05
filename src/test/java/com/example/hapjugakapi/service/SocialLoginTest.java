package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.SocialClient;
import com.example.hapjugakapi.config.SocialCode;
import com.example.hapjugakapi.config.SocialUser;
import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.repository.MemberRepository;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

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
        authService = new AuthService(memberRepository, passwordEncoder, jwtTokenProvider, 가짜들);
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
        // 자체 가입한 계정이므로 "이메일과 비밀번호로" 들어오라고 해야 한다
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

        Member 뒤 = memberRepository.findById(피해자.getId()).orElseThrow();
        assertEquals(AuthProvider.LOCAL, 뒤.getProvider(), "제공자가 바뀌면 안 된다");
        assertNull(뒤.getProviderId(), "소셜 계정이 이어붙으면 안 된다");
        assertEquals("피해자", 뒤.getNickname());
    }

    @Test
    @DisplayName("네이버 이메일은 인증 여부를 알 수 없으므로 잇지 않는다")
    void 네이버는_잇지_않는다() {
        memberRepository.save(new Member("both@test.com", "{noop}pw", "먼저온사람", "서울", ""));
        제공자가답한다(AuthProvider.NAVER,
                new SocialUser("naver-1", "both@test.com", false, "같은사람일수도"));

        assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.NAVER, 아무코드));
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
        assertEquals(AuthProvider.GOOGLE, 들어온사람.getProvider());
        assertEquals("google-1", 들어온사람.getProviderId());
    }

    @Test
    @DisplayName("처음 보는 사람이면 계정을 만든다")
    void 처음이면_만든다() {
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-new", "new@test.com", true, "새사람"));

        Member 만들어진사람 = 로그인(AuthProvider.KAKAO);

        assertEquals(AuthProvider.KAKAO, 만들어진사람.getProvider());
        assertEquals("kakao-new", 만들어진사람.getProviderId());
        assertEquals("new@test.com", 만들어진사람.getEmail());
        assertEquals("새사람", 만들어진사람.getNickname());
    }

    @Test
    @DisplayName("이메일을 안 줘도 가입된다 — 카카오·네이버는 선택 동의다")
    void 이메일이_없어도_된다() {
        제공자가답한다(AuthProvider.KAKAO,
                new SocialUser("kakao-noemail", null, false, "메일안준사람"));

        Member 만들어진사람 = 로그인(AuthProvider.KAKAO);

        assertNull(만들어진사람.getEmail());
        assertEquals("kakao-noemail", 만들어진사람.getProviderId());
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
    @DisplayName("탈퇴한 사람이 같은 소셜 계정으로 돌아오면 새 회원이 된다")
    void 탈퇴한_뒤_다시_오면() {
        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-back", "back@test.com", true, "돌아올사람"));
        Long 옛id = 로그인(AuthProvider.GOOGLE).getId();

        memberRepository.findById(옛id).orElseThrow().withdraw(java.time.LocalDateTime.now());

        Long 새id = 로그인(AuthProvider.GOOGLE).getId();

        assertNotEquals(옛id, 새id, "탈퇴한 행을 되살리면 안 된다");
    }

    @Test
    @DisplayName("이미 다른 소셜이 붙어 있으면 덮어쓰지 않는다")
    void 먼저_붙은_연결을_지키다() {
        // 네이버로 가입했다. 이메일도 같이 왔다.
        제공자가답한다(AuthProvider.NAVER,
                new SocialUser("naver-9", "dual@test.com", false, "두계정쓰는사람"));
        Long id = 로그인(AuthProvider.NAVER).getId();

        // 같은 주소의 구글로 들어온다. 구글은 주소를 확인해 준다.
        제공자가답한다(AuthProvider.GOOGLE,
                new SocialUser("google-9", "dual@test.com", true, "두계정쓰는사람"));

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.GOOGLE, 아무코드));
        assertTrue(e.getMessage().contains("네이버"), "어느 쪽으로 들어오라는지 알려줘야 한다");

        // 덮어썼다면 네이버 연결이 끊겨 자기 계정에 못 들어가게 된다.
        Member 그대로 = memberRepository.findById(id).orElseThrow();
        assertEquals(AuthProvider.NAVER, 그대로.getProvider());
        assertEquals("naver-9", 그대로.getProviderId());

        // 네이버로는 여전히 들어가진다.
        제공자가답한다(AuthProvider.NAVER,
                new SocialUser("naver-9", "dual@test.com", false, "두계정쓰는사람"));
        assertEquals(id, 로그인(AuthProvider.NAVER).getId());
    }

    @Test
    @DisplayName("자체 로그인 길로는 소셜 제공자를 부를 수 없다")
    void 로컬은_소셜이_아니다() {
        assertThrows(UnauthorizedException.class,
                () -> authService.socialLogin(AuthProvider.LOCAL, 아무코드));
    }
}
