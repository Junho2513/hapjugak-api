package com.example.hapjugakapi.service;

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

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!member.hasPassword()) {
            throw new UnauthorizedException("구글로 가입한 계정입니다. 구글 로그인을 이용해주세요.");
        }
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return issue(member);
    }

    @Transactional
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(request.idToken());

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