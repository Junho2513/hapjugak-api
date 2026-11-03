package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByMemberId(Long memberId);

    /** 이 사람이 그 일정의 어느 곡에든 자리를 잡고 있는가. 비공개 일정 접근 판단에 쓴다. */
    boolean existsBySongEventIdAndMemberId(Long eventId, Long memberId);
}