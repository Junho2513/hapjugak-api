package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 중에 "지금 이게 되나?"를 확인하는 자리.
 *
 * 메일은 설정이 많다 - IAM 정책, 발신 주소, DKIM, 리전.
 * 하나만 틀어져도 안 가는데, 기능을 만들어 놓고 회원 가입으로 확인하면
 * 코드가 틀린 건지 설정이 틀린 건지 가려내기 어렵다.
 * 그래서 설정만 확인하는 길을 따로 둔다.
 */
@Service
@Transactional(readOnly = true)
public class OpsService {

    private final MemberRepository memberRepository;
    private final MailService mailService;

    public OpsService(MemberRepository memberRepository, MailService mailService) {
        this.memberRepository = memberRepository;
        this.mailService = mailService;
    }

    /**
     * 요청한 관리자 본인에게 예시 메일을 보낸다.
     *
     * 받는 주소를 입력받지 않는 것이 중요하다.
     * 주소를 받게 만들면 관리자 토큰이 한 번 새는 순간
     * 우리 도메인 이름으로 아무 주소에나 메일을 뿌릴 수 있는 길이 된다.
     *
     * @return 메일을 보낸 주소
     */
    public String sendTestMail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        if (member.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자만 메일 발송을 점검할 수 있습니다.");
        }
        mailService.sendTest(member.getEmail());
        return member.getEmail();
    }
}
