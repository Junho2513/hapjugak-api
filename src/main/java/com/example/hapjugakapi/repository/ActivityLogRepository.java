package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop200BySongIdOrderByCreatedAtDesc(Long songId);
}