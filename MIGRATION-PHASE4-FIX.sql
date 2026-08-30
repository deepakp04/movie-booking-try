-- =====================================================================
--  FIX for MIGRATION-PHASE4.sql section 3
--
--  The original DELETE failed with:
--    ERROR 1093: You can't specify target table 'ss' for update in FROM clause
--
--  MySQL forbids referencing the table being deleted inside a subquery of the
--  same statement. Sections 1, 2 and 4 of the original script succeeded, so
--  only this part needs re-running. A temporary table breaks the self-reference.
--
--  Usage:  mysql -u root -p movie_booking_system < MIGRATION-PHASE4-FIX.sql
-- =====================================================================

USE movie_booking_system;

-- Shows that are in the future AND completely untouched: no held seat, no
-- booked seat, no booking reference anywhere in the show. Clearing their seat
-- maps lets them regenerate from the real screen_seats layout on next view.
DROP TEMPORARY TABLE IF EXISTS tmp_clearable_shows;

CREATE TEMPORARY TABLE tmp_clearable_shows AS
SELECT ss.show_id
FROM show_seats ss
JOIN shows s ON s.id = ss.show_id
WHERE s.start_time > NOW()
GROUP BY ss.show_id
HAVING SUM(CASE WHEN ss.status <> 'AVAILABLE' OR ss.booking_id IS NOT NULL
                THEN 1 ELSE 0 END) = 0;

SELECT COUNT(*) AS shows_to_be_cleared FROM tmp_clearable_shows;

DELETE FROM show_seats
WHERE show_id IN (SELECT show_id FROM tmp_clearable_shows);

DROP TEMPORARY TABLE tmp_clearable_shows;

-- ---------------------------------------------------------------------
-- Verification
-- ---------------------------------------------------------------------
SELECT 'show_seats remaining'   AS metric, COUNT(*) AS value FROM show_seats
UNION ALL SELECT 'missing price',          COUNT(*) FROM show_seats WHERE price IS NULL
UNION ALL SELECT 'linked to screen_seats', COUNT(*) FROM show_seats WHERE screen_seat_id IS NOT NULL
UNION ALL SELECT 'screen_seats defined',   COUNT(*) FROM screen_seats;

-- Any show whose seats were cleared will regenerate from the drawn layout the
-- next time its seat map is opened, and those new rows WILL be linked.
--
-- Rows that remain unlinked belong to past shows or shows with live holds or
-- bookings. That is correct: their seat maps must not change.
