-- =====================================================================
--  PHASE 4 MIGRATION - booking reads the real seat grid
--  Run AFTER MIGRATION-PHASE3.sql, and BEFORE starting the app.
--
--  Usage:  mysql -u root -p movie_booking_system < MIGRATION-PHASE4.sql
-- =====================================================================

USE movie_booking_system;

-- ---------------------------------------------------------------------
-- 1. Link each show-seat to the physical seat it came from, and snapshot
--    the tier name and price at materialization time.
--
--    price is the money column. It is written once, when a show's seat map
--    is first generated, and never joined back to live tier prices. That is
--    what keeps revenue reporting truthful when someone edits a price later.
-- ---------------------------------------------------------------------
ALTER TABLE show_seats
  ADD COLUMN screen_seat_id BIGINT        DEFAULT NULL,
  ADD COLUMN tier_name      VARCHAR(50)   DEFAULT NULL,
  ADD COLUMN price          DECIMAL(10,2) DEFAULT NULL;

ALTER TABLE show_seats
  ADD CONSTRAINT fk_show_seat_screen_seat
  FOREIGN KEY (screen_seat_id) REFERENCES screen_seats (id);

CREATE INDEX idx_show_seat_screen_seat ON show_seats (screen_seat_id);

-- ---------------------------------------------------------------------
-- 2. Backfill price for seats materialized before this change so that any
--    existing pending or confirmed booking still has a per-seat amount.
--    Falls back to the show's base price, which is what those bookings were
--    actually charged at.
-- ---------------------------------------------------------------------
UPDATE show_seats ss
JOIN shows s ON s.id = ss.show_id
SET ss.price = s.base_price
WHERE ss.price IS NULL;

-- ---------------------------------------------------------------------
-- 3. OPTIONAL but recommended.
--
--    Shows whose seat map was materialized under the old hardcoded 10-per-row
--    grid will keep that grid forever, because seats are only generated once.
--    Clearing the seat maps of FUTURE, UNBOOKED shows lets them regenerate
--    from the real layout you drew in the Maintenance tab.
--
--    This deletes nothing that anyone has booked or is currently holding.
-- ---------------------------------------------------------------------
DELETE ss FROM show_seats ss
JOIN shows s ON s.id = ss.show_id
WHERE s.start_time > NOW()
  AND ss.status = 'AVAILABLE'
  AND ss.booking_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM show_seats x
      WHERE x.show_id = ss.show_id
        AND (x.status <> 'AVAILABLE' OR x.booking_id IS NOT NULL)
  );

-- ---------------------------------------------------------------------
-- 4. Verification
-- ---------------------------------------------------------------------
SELECT 'show_seats total'        AS metric, COUNT(*) AS value FROM show_seats
UNION ALL SELECT 'missing price',           COUNT(*) FROM show_seats WHERE price IS NULL
UNION ALL SELECT 'linked to screen_seats',  COUNT(*) FROM show_seats WHERE screen_seat_id IS NOT NULL
UNION ALL SELECT 'screen_seats defined',    COUNT(*) FROM screen_seats;

-- "missing price" should be 0.
-- "linked to screen_seats" will be 0 until shows regenerate their seat maps,
-- which happens the next time someone opens each show's seat map.
