-- =====================================================================
--  RUN THIS BEFORE STARTING THE APP FOR THE FIRST TIME AFTER UPGRADING
-- =====================================================================
--
--  Why manually and not via ddl-auto=update:
--  Hibernate would try to ADD COLUMN ... NOT NULL with no default onto
--  tables that already contain rows. Depending on MySQL's strict mode that
--  either fails outright or backfills zero-dates. Adding the columns here
--  WITH defaults means Hibernate finds them already present and leaves
--  them alone.
--
--  Run against:  movie_booking_system
--  Usage:        mysql -u root -p movie_booking_system < MIGRATION.sql
-- =====================================================================

USE movie_booking_system;

-- ---------------------------------------------------------------------
-- 1. Soft-delete + audit columns for the five entities that now extend
--    BaseEntity: theatres, screens, shows, movies, cities.
-- ---------------------------------------------------------------------

ALTER TABLE theatres
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN is_deleted BIT(1)      NOT NULL DEFAULT b'0';

ALTER TABLE screens
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN is_deleted BIT(1)      NOT NULL DEFAULT b'0';

ALTER TABLE shows
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN is_deleted BIT(1)      NOT NULL DEFAULT b'0';

ALTER TABLE movies
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN is_deleted BIT(1)      NOT NULL DEFAULT b'0';

ALTER TABLE cities
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN is_deleted BIT(1)      NOT NULL DEFAULT b'0';

-- ---------------------------------------------------------------------
-- 2. Backfill safety net.
--    In SQL, `NULL = FALSE` evaluates to NULL, not TRUE. So any row where
--    is_deleted is NULL is invisible to findByIsDeletedFalse... queries and
--    would silently disappear from the admin portal. This guarantees 0 NULLs
--    even if a column was added some other way.
-- ---------------------------------------------------------------------

UPDATE theatres SET is_deleted = b'0' WHERE is_deleted IS NULL;
UPDATE screens  SET is_deleted = b'0' WHERE is_deleted IS NULL;
UPDATE shows    SET is_deleted = b'0' WHERE is_deleted IS NULL;
UPDATE movies   SET is_deleted = b'0' WHERE is_deleted IS NULL;
UPDATE cities   SET is_deleted = b'0' WHERE is_deleted IS NULL;

UPDATE theatres SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE theatres SET updated_at = NOW(6) WHERE updated_at IS NULL;
UPDATE screens  SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE screens  SET updated_at = NOW(6) WHERE updated_at IS NULL;
UPDATE shows    SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE shows    SET updated_at = NOW(6) WHERE updated_at IS NULL;
UPDATE movies   SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE movies   SET updated_at = NOW(6) WHERE updated_at IS NULL;
UPDATE cities   SET created_at = NOW(6) WHERE created_at IS NULL;
UPDATE cities   SET updated_at = NOW(6) WHERE updated_at IS NULL;

-- ---------------------------------------------------------------------
-- 3. Verification. Every row should report nulls = 0.
-- ---------------------------------------------------------------------

SELECT 'theatres' AS tbl, COUNT(*) AS total, SUM(is_deleted IS NULL) AS nulls FROM theatres
UNION ALL SELECT 'screens', COUNT(*), SUM(is_deleted IS NULL) FROM screens
UNION ALL SELECT 'shows',   COUNT(*), SUM(is_deleted IS NULL) FROM shows
UNION ALL SELECT 'movies',  COUNT(*), SUM(is_deleted IS NULL) FROM movies
UNION ALL SELECT 'cities',  COUNT(*), SUM(is_deleted IS NULL) FROM cities;

-- ---------------------------------------------------------------------
-- 4. OPTIONAL: find duplicate theatres created by the retry bug that has
--    now been removed from admin.js. Review before deleting anything.
-- ---------------------------------------------------------------------

SELECT name, address, COUNT(*) AS copies, GROUP_CONCAT(id) AS ids
FROM theatres
WHERE is_deleted = b'0'
GROUP BY name, address
HAVING COUNT(*) > 1;

-- Then soft-delete the extras by id, for example:
--   UPDATE theatres SET is_deleted = b'1' WHERE id IN (5, 9);

-- ---------------------------------------------------------------------
-- NOTE: movie_languages and movie_formats are new join tables created by
-- Hibernate on startup (ddl-auto=update). No manual DDL needed for them.
-- Movies that already existed will have empty language/format sets and will
-- render as an em dash until you edit them from the Movie Library.
-- ---------------------------------------------------------------------
