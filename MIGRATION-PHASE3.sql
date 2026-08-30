-- =====================================================================
--  PHASE 3 MIGRATION - seat tiers, real seat rows, per-show tier pricing
--  Run AFTER MIGRATION.sql, and BEFORE starting the app.
--
--  Usage:  mysql -u root -p movie_booking_system < MIGRATION-PHASE3.sql
-- =====================================================================
--
--  Unlike MIGRATION.sql, everything here is a NEW table. Hibernate's
--  ddl-auto=update creates new tables safely on its own, so strictly speaking
--  you could skip this file and let it do the work.
--
--  It is provided because Hibernate will NOT create the unique constraints or
--  the composite indexes that make the seat map fast and prevent duplicate
--  positions. Running this first gets you the constraints; Hibernate then finds
--  the tables already present and leaves them alone.
-- =====================================================================

USE movie_booking_system;

-- ---------------------------------------------------------------------
-- 1. Pricing tiers, scoped to a screen.
--    Screen-scoped rather than theatre-scoped because two screens in the
--    same multiplex routinely have different seating classes.
--    No price column: pricing is per show (table 3).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seat_tiers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    screen_id     BIGINT       NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    color_hex     VARCHAR(7)            DEFAULT '#7A7A7A',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_deleted    BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_tier_screen_name UNIQUE (screen_id, name),
    CONSTRAINT fk_seat_tier_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    INDEX idx_seat_tier_screen (screen_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. The seat grid, one row per physical position.
--    Replaces Screen.layout_json as the source of truth. Storing seats as
--    rows is what makes per-seat CRUD and per-seat tiers expressible.
--
--    seat_number is NULL for PATHWAY cells and counts only sellable cells
--    within a row, so a gangway mid-row still yields C1..Cn with no gap.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS screen_seats (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    screen_id    BIGINT      NOT NULL,
    seat_tier_id BIGINT               DEFAULT NULL,
    row_label    VARCHAR(4)  NOT NULL,
    col_index    INT         NOT NULL,
    seat_number  INT                  DEFAULT NULL,
    seat_type    VARCHAR(20) NOT NULL DEFAULT 'SEAT',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_deleted   BIT(1)      NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    CONSTRAINT uk_screen_seat_position UNIQUE (screen_id, row_label, col_index),
    CONSTRAINT fk_screen_seat_screen FOREIGN KEY (screen_id)    REFERENCES screens (id),
    CONSTRAINT fk_screen_seat_tier   FOREIGN KEY (seat_tier_id) REFERENCES seat_tiers (id),
    INDEX idx_screen_seat_screen (screen_id, is_deleted),
    INDEX idx_screen_seat_tier (seat_tier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. Price of one tier for one show.
--    Price is a property of the screening, not the furniture: a DIAMOND seat
--    costs more on a Saturday night than a Tuesday matinee.
--
--    Seats fall back to shows.base_price when no row exists here, which is
--    what keeps pre-existing shows sellable with no backfill.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_tier_prices (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    show_id      BIGINT         NOT NULL,
    seat_tier_id BIGINT         NOT NULL,
    price        DECIMAL(10,2)  NOT NULL,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_deleted   BIT(1)         NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    CONSTRAINT uk_show_tier_price UNIQUE (show_id, seat_tier_id),
    CONSTRAINT fk_show_tier_price_show FOREIGN KEY (show_id)      REFERENCES shows (id),
    CONSTRAINT fk_show_tier_price_tier FOREIGN KEY (seat_tier_id) REFERENCES seat_tiers (id),
    INDEX idx_show_tier_price_show (show_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. Verification
-- ---------------------------------------------------------------------
SELECT 'seat_tiers'       AS tbl, COUNT(*) AS rows_present FROM seat_tiers
UNION ALL SELECT 'screen_seats',     COUNT(*) FROM screen_seats
UNION ALL SELECT 'show_tier_prices', COUNT(*) FROM show_tier_prices;

-- All three should report 0. You create tiers and draw layouts from the
-- Maintenance tab in the admin portal.

-- ---------------------------------------------------------------------
-- NOTES
--
-- screens.layout_json is intentionally left in place but is no longer read by
-- anything. It stays for one release as a rollback path; once you have redrawn
-- every screen from the Maintenance tab it can be dropped:
--     ALTER TABLE screens DROP COLUMN layout_json;
--
-- Phase 4 will add screen_seat_id, tier_name and price to show_seats so that
-- booking reads the real grid and snapshots the price actually paid.
-- ---------------------------------------------------------------------
