-- ============================================================
-- Migration: Update HELD seats for CONFIRMED bookings to BOOKED
-- ============================================================
-- When a payment was confirmed before the seat-status fix,
-- booking.status = CONFIRMED but show_seats.status stayed HELD.
-- This migration corrects those orphaned seats so analytics
-- revenue counting (which filters on ss.status = 'BOOKED')
-- reflects actual confirmed payments.
--
-- Safe to run multiple times (idempotent).
-- ============================================================

-- 1. Preview: how many seats will be updated?
SELECT ss.id, ss.seat_code, ss.status AS current_status, b.id AS booking_id, b.status AS booking_status
FROM show_seats ss
JOIN bookings b ON ss.booking_id = b.id
WHERE ss.status = 'HELD'
  AND b.status = 'CONFIRMED'
LIMIT 50;

-- 2. Run the update
UPDATE show_seats ss
JOIN bookings b ON ss.booking_id = b.id
SET ss.status = 'BOOKED'
WHERE ss.status = 'HELD'
  AND b.status = 'CONFIRMED';

-- 3. Verify: should return 0 rows after update
SELECT COUNT(*) AS remaining_held_for_confirmed
FROM show_seats ss
JOIN bookings b ON ss.booking_id = b.id
WHERE ss.status = 'HELD'
  AND b.status = 'CONFIRMED';
