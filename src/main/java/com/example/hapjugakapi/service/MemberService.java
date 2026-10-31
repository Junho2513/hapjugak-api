package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Instrument;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.UploadKind;
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
    private final StorageService storageService;

    public MemberService(MemberRepository memberRepository,
                         PasswordEncoder passwordEncoder,
                         StorageService storageService) {
        this.memberRepository = memberRepository;
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
        Member target = getMember(targetId);
        String photo = target.getPhotoUrl();
        memberRepository.delete(target);
        storageService.deleteByUrl(photo);
    }

    private void applyProfile(Member member, MemberRequest request) {
        applyPhoto(member, request.photoUrl());
        if (request.instruments() != null) {
            member.getInstruments().clear();
            for (Instrument instrument : request.instruments()) {
                member.addInstrument(instrument);
            }
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
