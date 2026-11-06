package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.JwtTokenProvider;
import com.example.hapjugakapi.config.SocialClient;
import com.example.hapjugakapi.config.SocialCode;
import com.example.hapjugakapi.config.SocialUser;
import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.SocialAccount;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SocialAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 제공자별 구현체. 스프링이 찾아준 것을 제공자를 열쇠로 놓아둔다.
     *
     * 새 제공자를 붙일 때 이 클래스를 건드리지 않아도 되는 것이 요점이다.
     * SocialClient 를 구현한 @Component 를 하나 더 만들면 여기 저절로 들어온다.
     */
    private final Map<AuthProvider, SocialClient> clients = new EnumMap<>(AuthProvider.class);

    public AuthService(MemberRepository memberRepository,
                       SocialAccountRepository socialAccountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       List<SocialClient> socialClients) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        socialClients.forEach(c -> this.clients.put(c.provider(), c));
    }

    /**
     * 자체 로그인.
     *
     * 아이디가 없을 때와 비밀번호가 틀렸을 때를 나눠서 알려준다.
     *
     * 둘을 합쳐 "이메일 또는 비밀번호가 올바르지 않습니다" 하나로 두는 방식도 있다.
     * 그렇게 하면 공격자가 "이 이메일이 가입돼 있는지"를 알아낼 수 없다.
     * 대신 사용자는 오타를 어디서 냈는지 알 수 없어, 맞는 비밀번호를 몇 번이고 다시 친다.
     *
     * 나눠 알려주기로 한 이유는 이렇다.
     *  - 가입 화면이 이미 "이미 가입된 이메일입니다"(409)를 돌려준다.
     *    로그인만 가려봐야 그쪽으로 똑같이 알아낼 수 있다.
     *  - 아는 사람들끼리 쓰는 합주 서비스라 이메일 목록 자체가 비밀이 아니다.
     *
     * 다만 이 선택은 "로그인 요청 제한"이 있어야 안전하다.
     * 그게 없으면 이메일 목록을 빠르게 훑는 걸 막을 방법이 없다.
     * 요청 제한을 넣기 전까지는 이 결정을 다시 볼 것.
     */
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("아이디(이메일 주소)를 확인해주세요."));

        if (!member.hasPassword()) {
            // 어느 쪽으로 가입했는지를 보고 말한다.
            // 전에는 "구글로 가입한 계정입니다" 가 박혀 있었다. 구글만 있던 시절의
            // 문구인데, 카카오·네이버가 붙은 뒤에도 그대로여서 카카오로 가입한
            // 사람에게도 구글을 쓰라고 했다.
            throw new UnauthorizedException(
                    providerName(member.getProvider()) + "로 가입한 계정입니다. "
                            + providerName(member.getProvider()) + " 로그인을 이용해주세요.");
        }
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new UnauthorizedException("비밀번호를 확인해주세요.");
        }
        return issue(member);
    }

    /**
     * 소셜 로그인 — 구글·카카오·네이버가 모두 이 길로 들어온다.
     *
     * 두 단계다.
     *  1. 브라우저가 받아온 인가 코드를 제공자에게 되물어 "누구인지"로 바꾼다
     *  2. 그 사람이 우리 쪽에서 누구인지 정한다
     *
     * 1번은 제공자마다 다르므로 SocialClient 구현체에 맡긴다.
     * 여기서 하는 일은 2번이고, 그게 이 메서드의 전부다.
     */
    @Transactional
    public TokenResponse socialLogin(AuthProvider provider, SocialCode code) {
        return issue(resolve(provider, exchange(provider, code)));
    }

    /**
     * 「이 소셜 계정은 우리 쪽 누구인가」.
     *
     * 전에는 한 줄이었다.
     *
     *     memberRepository.findByEmail(google.email()).map(기존 계정에 연결)
     *
     * 소셜이 말하는 이메일만 보고 같은 주소의 기존 계정에 그대로 로그인시켰다.
     * 구글만 있을 때는 큰 문제가 아니었다 — 구글은 이메일을 확인하고 준다.
     * (그 클레임을 읽지도 않았으니, 「대체로 그렇다」를 근거로 계정을 넘기고 있었다.)
     *
     * 카카오가 붙으면 그게 구멍이 된다. 카카오계정 이메일은 미인증일 수 있다.
     * 공격자가 자기 카카오 계정의 이메일을 피해자 주소로 바꾸고 로그인하면
     * 그 사람 계정에 들어간다. 자체 가입한 회원이 특히 위험하다 —
     * 비밀번호를 몰라도 들어갈 수 있게 된다.
     *
     * 그래서 네 갈래로 나눈다.
     */
    private Member resolve(AuthProvider provider, SocialUser user) {
        // ① 이 소셜 계정으로 전에 들어온 적이 있다.
        //    이메일이 바뀌었든 이름이 바뀌었든 같은 사람이다.
        Optional<SocialAccount> known =
                socialAccountRepository.findWithMember(provider, user.providerId());
        if (known.isPresent()) {
            return known.get().getMember();
        }

        // 이메일이 없으면 이어붙일 실마리가 없다. 새 사람으로 받는다.
        // (카카오·네이버는 이메일 제공이 선택 동의라 실제로 자주 일어난다)
        if (user.email() == null || user.email().isBlank()) {
            return createWith(provider, user, null);
        }

        Optional<Member> sameEmail = memberRepository.findByEmail(user.email());
        if (sameEmail.isEmpty()) {
            // ② 처음 보는 사람이다.
            return createWith(provider, user, user.email());
        }

        Member existing = sameEmail.get();

        // ③ 같은 주소의 계정이 있고, 제공자가 그 주소를 확인해 줬다.
        //
        //    전에는 여기서 「이미 다른 소셜이 붙어 있으면 막는다」를 했다.
        //    연결 칸이 하나뿐이어서 이어붙이면 먼저 것이 끊겼기 때문이다.
        //    이제는 연결이 행이라 하나 더 붙이면 그만이다.
        if (user.hasTrustedEmail()) {
            link(existing, provider, user.providerId());
            return existing;
        }

        // ④ 같은 주소의 계정이 있는데 제공자가 확인해 주지 않았다.
        //
        // 여기서 멈춘다. 확인되지 않은 주소를 믿고 이어붙이면, 공격자가 자기
        // 소셜 계정의 이메일을 남의 주소로 바꾸는 것만으로 그 계정에 들어간다.
        //
        // 네이버가 늘 여기에 걸린다. 네이버는 이메일이 본인 확인을 거친 것인지
        // 알려주지 않기 때문이다. 그래서 「로그인한 뒤 직접 잇기」를 안내한다 —
        // 이미 본인임이 증명된 상태에서는 이메일을 믿을 필요가 없다(linkTo).
        throw new UnauthorizedException(
                "이미 " + user.email() + " 로 가입된 계정이 있습니다. "
                        + howToSignIn(existing) + " 로그인한 뒤, "
                        + "회원 정보에서 " + providerName(provider) + " 계정을 연결해 주세요.");
    }

    /** 새 회원을 만들고 연결을 하나 붙인다. */
    private Member createWith(AuthProvider provider, SocialUser user, String email) {
        Member member = memberRepository.save(
                Member.social(provider, email, user.nickname(), user.providerId()));
        socialAccountRepository.save(new SocialAccount(member, provider, user.providerId()));
        return member;
    }

    /**
     * 연결을 붙인다. 같은 제공자로 이미 이어둔 것이 있으면 고유번호만 고친다.
     *
     * 고치는 쪽이 필요한 이유는 Firestore 이관 때문이다. 옮겨온 연결에는
     * 구글의 sub 가 아니라 Firebase UID 가 들어 있다. 진짜 값은 그 사람이
     * 로그인해 봐야 알 수 있으므로, 알게 된 순간 바꿔 둔다.
     * 한 번 고쳐지면 다음부터는 ①에서 바로 찾아진다.
     *
     * 새 행을 만들지 않는 것이 중요하다. (member_id, provider) 가 유니크라
     * 만들려 하면 DB 가 거절한다.
     */
    private void link(Member member, AuthProvider provider, String providerId) {
        socialAccountRepository.findByMemberIdAndProvider(member.getId(), provider)
                .ifPresentOrElse(
                        existing -> existing.changeProviderId(providerId),
                        () -> socialAccountRepository.save(
                                new SocialAccount(member, provider, providerId)));
    }

    /**
     * 로그인한 사람이 자기 계정에 소셜 계정을 직접 잇는다.
     *
     * 이 길이 필요한 이유는 네이버 때문이다. 네이버는 이메일이 본인 확인을 거친
     * 것인지 알려주지 않아서, 이메일만으로는 영영 자동 연결할 수 없다.
     * 그런데 이미 로그인한 사람이라면 본인임이 증명돼 있다. 그때는 이메일을
     * 믿을 필요가 없으므로 안전하게 붙일 수 있다.
     */
    @Transactional
    public void linkTo(Long memberId, AuthProvider provider, SocialCode code) {
        SocialUser user = exchange(provider, code);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        socialAccountRepository.findWithMember(provider, user.providerId())
                .ifPresent(already -> {
                    if (!already.getMember().getId().equals(memberId)) {
                        // 남의 계정에 붙어 있는 소셜 계정을 가져올 수는 없다.
                        throw new ConflictException("이 " + providerName(provider)
                                + " 계정은 이미 다른 회원에게 연결돼 있습니다.");
                    }
                });

        link(member, provider, user.providerId());
    }

    /**
     * 연결을 끊는다.
     *
     * 마지막 들어오는 길은 끊지 못하게 막는다. 비밀번호가 없는 사람이 하나뿐인
     * 소셜 연결을 끊으면 자기 계정에 다시는 못 들어간다. 되돌릴 길도 없다.
     */
    @Transactional
    public void unlink(Long memberId, AuthProvider provider) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        List<SocialAccount> links =
                socialAccountRepository.findByMemberIdOrderByProviderAsc(memberId);
        SocialAccount target = links.stream()
                .filter(sa -> sa.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        providerName(provider) + " 계정이 연결돼 있지 않습니다."));

        if (links.size() == 1 && !member.hasPassword()) {
            throw new ConflictException("마지막 로그인 방법은 끊을 수 없습니다. "
                    + "다른 계정을 먼저 연결하거나 비밀번호를 설정해 주세요.");
        }

        socialAccountRepository.delete(target);
    }

    /** 이 회원이 들고 있는 연결. 회원 정보 화면이 쓴다. */
    public List<AuthProvider> linkedProviders(Long memberId) {
        return socialAccountRepository.findByMemberIdOrderByProviderAsc(memberId).stream()
                .map(SocialAccount::getProvider)
                .toList();
    }

    private SocialUser exchange(AuthProvider provider, SocialCode code) {
        SocialClient client = clients.get(provider);
        if (client == null) {
            throw new UnauthorizedException("지원하지 않는 로그인 방법입니다.");
        }
        return client.exchange(code);
    }

    /**
     * 이 계정에 원래 붙어 있는 들어오는 길. ④ 안내문에 넣는다.
     *
     * 조사까지 넣어 돌려준다. 바깥에서 "으로" 를 붙이면 「카카오으로」가 된다 —
     * 받침에 따라 「로」와 「으로」가 갈리므로, 말을 아는 쪽에서 완성해 준다.
     */
    private static String howToSignIn(Member member) {
        if (member.isLinkedWithSocial()) {
            return switch (member.getProvider()) {
                case GOOGLE, KAKAO, NAVER -> providerName(member.getProvider()) + "로";
                default -> "원래 쓰시던 방법으로";
            };
        }
        return "이메일과 비밀번호로";
    }

    /** 사람에게 보여줄 제공자 이름. */
    private static String providerName(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> "구글";
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
            case LOCAL -> "이메일";
        };
    }

    private TokenResponse issue(Member member) {
        String token = jwtTokenProvider.createToken(member.getId(), member.getRole().name());
        return new TokenResponse(
                token,
                "Bearer",
                member.getId(),
                member.getNickname(),
                member.getRole()
        );
    }
}