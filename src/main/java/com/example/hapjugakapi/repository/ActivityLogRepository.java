package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
}