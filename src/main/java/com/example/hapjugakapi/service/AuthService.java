package com.example.hapjugakapi.service;

import com.example.hapjugakapi.config.GoogleOAuthClient;
import com.example.hapjugakapi.config.GoogleTokenVerifier;
import com.example.hapjugakapi.config.JwtTokenProvider;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.GoogleLoginRequest;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleOAuthClient googleOAuthClient;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       GoogleTokenVerifier googleTokenVerifier,
                       GoogleOAuthClient googleOAuthClient) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleTokenVerifier = googleTokenVerifier;
        this.googleOAuthClient = googleOAuthClient;
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
     * 구글 로그인.
     *
     * 두 단계다.
     *  1. 브라우저가 받아온 인가 코드를 구글에 되물어 ID 토큰으로 바꾼다
     *  2. 그 ID 토큰의 서명을 구글 공개키로 검증한다
     *
     * 2번을 생략하지 않는 이유 — 1번의 응답은 구글에서 왔으니 믿어도 될 것 같지만,
     * 서명 검증은 iss(발급자)와 aud(우리 앱) 까지 확인한다.
     * 다른 앱용으로 발급된 토큰이 흘러드는 것을 막는 건 그 확인이다.
     */
    @Transactional
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        String idToken = googleOAuthClient.exchangeForIdToken(request.code());
        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(idToken);

        if (google.email() == null) {
            throw new UnauthorizedException("구글 계정에서 이메일을 가져오지 못했습니다.");
        }

        Member member = memberRepository.findByEmail(google.email())
                .map(existing -> link(existing, google.sub()))
                .orElseGet(() -> memberRepository.save(
                        Member.google(google.email(), google.name(), google.sub())
                ));

        return issue(member);
    }

    private Member link(Member member, String providerId) {
        if (!member.isLinkedWithGoogle()) {
            member.linkGoogle(providerId);
        }
        return member;
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