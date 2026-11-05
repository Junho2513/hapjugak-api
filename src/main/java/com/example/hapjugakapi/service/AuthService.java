package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.JwtTokenProvider;
import com.example.hapjugakapi.config.SocialClient;
import com.example.hapjugakapi.config.SocialCode;
import com.example.hapjugakapi.config.SocialUser;
import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.repository.MemberRepository;
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
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       List<SocialClient> socialClients) {
        this.memberRepository = memberRepository;
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
            throw new UnauthorizedException("구글로 가입한 계정입니다. 구글 로그인을 이용해주세요.");
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
        SocialClient client = clients.get(provider);
        if (client == null) {
            throw new UnauthorizedException("지원하지 않는 로그인 방법입니다.");
        }
        return issue(resolve(provider, client.exchange(code)));
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
        // ① 전에 이 제공자로 들어온 적이 있다. 이메일이 바뀌었든 말든 같은 사람이다.
        //    제공자 안의 고유 번호는 바뀌지 않으므로 이게 가장 확실한 기준이다.
        Optional<Member> known =
                memberRepository.findByProviderAndProviderId(provider, user.providerId());
        if (known.isPresent()) {
            return known.get();
        }

        // 이메일이 없으면 이어붙일 실마리가 없다. 새 사람으로 받는다.
        // (카카오·네이버는 이메일 제공이 선택 동의라 실제로 자주 일어난다)
        if (user.email() == null || user.email().isBlank()) {
            return memberRepository.save(
                    Member.social(provider, null, user.nickname(), user.providerId()));
        }

        Optional<Member> sameEmail = memberRepository.findByEmail(user.email());
        if (sameEmail.isEmpty()) {
            // ② 처음 보는 사람이다.
            return memberRepository.save(
                    Member.social(provider, user.email(), user.nickname(), user.providerId()));
        }

        Member existing = sameEmail.get();

        // ③ 같은 주소의 계정이 있고, 제공자가 그 주소를 확인해 줬고,
        //    아직 어떤 소셜도 붙어 있지 않다. 이어붙인다.
        //
        //    「아직 안 붙어 있을 때만」이 중요하다. 회원 한 명이 소셜 계정 하나만
        //    들 수 있게 되어 있어서(member.provider_id 칸 하나), 덮어쓰면 먼저
        //    붙어 있던 것이 사라진다. 네이버로 가입한 사람이 구글로 한 번 들어오면
        //    네이버 연결이 지워지고, 그 뒤로 네이버 로그인은 ④에 걸려 영영 막힌다.
        //    자기 계정에 자기가 못 들어가게 되는 것이다.
        if (user.hasTrustedEmail() && !existing.isLinkedWithSocial()) {
            existing.linkSocial(provider, user.providerId());
            return existing;
        }

        // ④ 그 밖의 경우 — 확인되지 않은 주소이거나, 이미 다른 소셜이 붙어 있다.
        //
        // 여기서 멈춘다. 이어붙이면 계정 탈취가 되거나 먼저 붙은 연결이 끊기고,
        // 모르는 척 새로 만들면 같은 사람의 계정이 말없이 둘로 갈라진다.
        // 둘 다 나중에 더 비싸다. 본인이라면 원래 쓰던 방법으로 들어오면 된다.
        throw new UnauthorizedException(
                "이미 " + user.email() + " 로 가입된 계정이 있습니다. "
                        + howToSignIn(existing) + " 로그인해 주세요.");
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
                case GOOGLE -> "구글로";
                case KAKAO -> "카카오로";
                case NAVER -> "네이버로";
                default -> "원래 쓰시던 방법으로";
            };
        }
        return "이메일과 비밀번호로";
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