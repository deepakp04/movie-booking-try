-- ============================================================
-- MIGRATION: User Profile Fields + Booking Attendees
-- Run this in MySQL against movie_booking_system database
-- ============================================================

-- 1. Add profile fields to users table
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL AFTER email;
ALTER TABLE users ADD COLUMN date_of_birth DATE NULL AFTER phone;

-- 2. Create booking_attendees table
CREATE TABLE booking_attendees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    seat_code VARCHAR(10) NOT NULL,
    attendee_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    phone VARCHAR(20) NULL,
    is_self BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

-- 3. Add index for faster lookups
CREATE INDEX idx_booking_attendees_booking_id ON booking_attendees(booking_id);
