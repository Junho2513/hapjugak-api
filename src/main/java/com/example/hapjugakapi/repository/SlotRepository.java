package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByMemberId(Long memberId);
}