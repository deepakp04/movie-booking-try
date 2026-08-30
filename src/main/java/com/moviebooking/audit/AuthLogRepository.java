package com.moviebooking.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthLogRepository
        extends JpaRepository<AuthLog, Long> {
}