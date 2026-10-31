package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.Role;
import com.example.hapjugakapi.domain.SiteSettings;
import com.example.hapjugakapi.dto.SettingsDto;
import com.example.hapjugakapi.exception.ForbiddenException;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SiteSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettingsService {

    private final SiteSettingsRepository siteSettingsRepository;
    private final MemberRepository memberRepository;

    public SettingsService(SiteSettingsRepository siteSettingsRepository,
                           MemberRepository memberRepository) {
        this.siteSettingsRepository = siteSettingsRepository;
        this.memberRepository = memberRepository;
    }

    public SettingsDto find() {
        return siteSettingsRepository.findById(SiteSettings.SINGLETON_ID)
                .map(SettingsDto::from)
                .orElseGet(() -> new SettingsDto(null));
    }

    @Transactional
    public SettingsDto update(SettingsDto request, Long memberId) {
        checkAdmin(memberId);
        SiteSettings settings = siteSettingsRepository.findById(SiteSettings.SINGLETON_ID)
                .orElseGet(() -> siteSettingsRepository.save(SiteSettings.createDefault()));
        if (request.heroImage() != null) {
            settings.setHeroImage(request.heroImage());
        }
        return SettingsDto.from(settings);
    }

    private void checkAdmin(Long memberId) {
        boolean admin = memberRepository.findById(memberId)
                .map(member -> member.getRole() == Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        if (!admin) {
            throw new ForbiddenException("관리자만 사이트 설정을 바꿀 수 있습니다.");
        }
    }
}