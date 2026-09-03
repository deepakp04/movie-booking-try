-- ============================================================
-- Migration: Link confirmed bookings to their seats
-- ============================================================
-- Problem: When seats expire (20-min hold), booking_id is cleared
-- and status reverts to AVAILABLE. But the booking is CONFIRMED.
-- This migration re-links seats and marks them BOOKED.
--
-- Safe to run multiple times (idempotent).
-- ============================================================

-- 1. Preview: seats that should be BOOKED for confirmed bookings
-- Uses FIND_IN_SET to match comma-separated seat_codes
SELECT ss.id, ss.seat_code, ss.status, ss.booking_id, ss.price,
       b.id AS booking_id_to_set, b.seat_codes, b.show_id
FROM bookings b
JOIN show_seats ss ON ss.show_id = b.show_id
  AND FIND_IN_SET(ss.seat_code, b.seat_codes) > 0
WHERE b.status = 'CONFIRMED'
  AND (ss.status != 'BOOKED' OR ss.booking_id IS NULL OR ss.booking_id != b.id);

-- 2. Run the update
UPDATE show_seats ss
JOIN bookings b ON ss.show_id = b.show_id
  AND FIND_IN_SET(ss.seat_code, b.seat_codes) > 0
SET ss.status = 'BOOKED',
    ss.booking_id = b.id
WHERE b.status = 'CONFIRMED'
  AND (ss.status != 'BOOKED' OR ss.booking_id IS NULL OR ss.booking_id != b.id);

-- 3. Verify: all confirmed bookings should now have BOOKED seats
SELECT b.id AS booking_id, b.seat_codes, b.total_amount,
       COUNT(ss.id) AS seats_booked,
       SUM(ss.price) AS calculated_revenue
FROM bookings b
JOIN show_seats ss ON ss.show_id = b.show_id
  AND FIND_IN_SET(ss.seat_code, b.seat_codes) > 0
WHERE b.status = 'CONFIRMED'
GROUP BY b.id, b.seat_codes, b.total_amount;

-- 4. Quick sanity check: total BOOKED seats
SELECT status, COUNT(*) AS cnt FROM show_seats GROUP BY status;
