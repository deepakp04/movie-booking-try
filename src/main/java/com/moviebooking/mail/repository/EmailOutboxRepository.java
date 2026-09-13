package com.moviebooking.mail.repository;

import com.moviebooking.mail.model.EmailOutbox;
import com.moviebooking.mail.model.EmailOutbox.EmailStatus;
import com.moviebooking.mail.model.EmailOutbox.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Find pending emails for processing with row-level lock to prevent
     * duplicate sends from concurrent processors.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailOutbox e WHERE e.status = 'PENDING' AND e.retryCount < e.maxRetries ORDER BY e.createdAt ASC")
    List<EmailOutbox> findPendingForProcessing();

    /**
     * Check if a confirmation email already exists for a booking (duplicate prevention).
     */
    boolean existsByBookingIdAndEmailTypeAndStatusIn(
            Long bookingId,
            EmailType emailType,
            List<EmailStatus> statuses
    );

    /**
     * Find outbox entry by booking and type (for idempotent checks).
     */
    Optional<EmailOutbox> findTopByBookingIdAndEmailTypeOrderByIdDesc(
            Long bookingId,
            EmailType emailType
    );
}
