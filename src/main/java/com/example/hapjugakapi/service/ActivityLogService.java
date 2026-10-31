package com.example.hapjugakapi.service;

import com.example.hapjugakapi.domain.ActivityLog;
import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.dto.ActivityLogRequest;
import com.example.hapjugakapi.dto.ActivityLogResponse;
import com.example.hapjugakapi.exception.NotFoundException;
import com.example.hapjugakapi.repository.ActivityLogRepository;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final SongRepository songRepository;
    private final MemberRepository memberRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository,
                              SongRepository songRepository,
                              MemberRepository memberRepository) {
        this.activityLogRepository = activityLogRepository;
        this.songRepository = songRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ActivityLogResponse create(Long songId, ActivityLogRequest request, Long actorId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new NotFoundException("곡을 찾을 수 없습니다."));
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        ActivityLog log = new ActivityLog(song, actor, request.type());
        log.describe(request.part(), request.detail(), request.targetName());
        return ActivityLogResponse.from(activityLogRepository.save(log));
    }

    public List<ActivityLogResponse> findBySong(Long songId) {
        return activityLogRepository.findTop200BySongIdOrderByCreatedAtDesc(songId).stream()
                .map(ActivityLogResponse::from)
                .toList();
    }
}