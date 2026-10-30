package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        Member member = new Member(
                request.email(),
                request.nickname(),
                request.area(),
                request.bio()
        );
        return MemberResponse.from(memberRepository.save(member));
    }

    public List<MemberResponse> findAll() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        member.setNickname(request.nickname());
        member.setArea(request.area());
        member.setBio(request.bio());
        return MemberResponse.from(member);
    }
}