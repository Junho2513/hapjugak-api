package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Instrument;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.exception.ConflictException;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
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

    public List<MemberResponse> findAll() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findById(Long id) {
        return MemberResponse.from(getMember(id));
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

    /** 관리자만 회원을 삭제할 수 있다. */
    @Transactional
    public void delete(Long targetId, Long actorId) {
        Member actor = getMember(actorId);
        if (actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자만 회원을 삭제할 수 있습니다.");
        }
        if (targetId.equals(actorId)) {
            throw new ConflictException("자기 자신은 삭제할 수 없습니다.");
        }
        memberRepository.delete(getMember(targetId));
    }

    private void applyProfile(Member member, MemberRequest request) {
        if (request.photoImage() != null) member.setPhotoImage(request.photoImage());
        if (request.instruments() != null) {
            member.getInstruments().clear();
            for (Instrument instrument : request.instruments()) {
                member.addInstrument(instrument);
            }
        }
    }

    private Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
    }
}