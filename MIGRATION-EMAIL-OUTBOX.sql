-- ============================================================
-- MIGRATION: Email Outbox for Async Booking Emails
-- Run this in MySQL against movie_booking_system database
-- ============================================================

CREATE TABLE email_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    email_type VARCHAR(30) NOT NULL,       -- BOOKING_CONFIRMED | BOOKING_CANCELLED
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    html_body LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    failure_reason VARCHAR(1000) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX idx_email_outbox_status ON email_outbox(status);
CREATE INDEX idx_email_outbox_booking_id ON email_outbox(booking_id);
