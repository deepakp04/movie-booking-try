package com.moviebooking.analytics.repository;

import com.moviebooking.analytics.dto.AnalyticsDTOs.AnalyticsFilter;
import com.moviebooking.analytics.dto.AnalyticsDTOs.*;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Native aggregation queries for analytics.
 * All revenue comes from show_seats.price (immutable snapshot).
 * No entity loading — returns scalar results only.
 */
@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager em;

    // ==================== SHARED FILTER CLAUSE ====================

    private record FilterClause(String sql, List<Object> params) {}

    private FilterClause buildFilter(AnalyticsFilter f, String showAlias) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();

        LocalDate dateFrom = f.effectiveDateFrom();
        LocalDate dateTo = f.effectiveDateTo();
        LocalDateTime fromDt = dateFrom.atStartOfDay();
        LocalDateTime toDt = dateTo.plusDays(1).atStartOfDay();

        // Hibernate 6 native queries use plain ? (not ?1, ?2) for positional params
        where.append(" AND ").append(showAlias).append(".start_time >= ?");
        params.add(fromDt);
        where.append(" AND ").append(showAlias).append(".start_time < ?");
        params.add(toDt);

        if (f.movieId() != null) {
            where.append(" AND ").append(showAlias).append(".movie_id = ?");
            params.add(f.movieId());
        }
        if (f.theatreId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id IN (SELECT scr.id FROM screens scr WHERE scr.theatre_id = ?)");
            params.add(f.theatreId());
        }
        if (f.screenId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id = ?");
            params.add(f.screenId());
        }
        if (f.cityId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id IN (SELECT scr.id FROM screens scr JOIN theatres t ON scr.theatre_id = t.id WHERE t.city_id = ?)");
            params.add(f.cityId());
        }
        if (f.format() != null && !f.format().isBlank()) {
            where.append(" AND ").append(showAlias).append(".format = ?");
            params.add(f.format());
        }
        if (f.language() != null && !f.language().isBlank()) {
            where.append(" AND ").append(showAlias).append(".language = ?");
            params.add(f.language());
        }

        return new FilterClause(where.toString(), params);
    }

    private String theatreScopeClause(Long restrictToTheatreId) {
        if (restrictToTheatreId == null) return "";
        return " AND s.screen_id IN (SELECT scr.id FROM screens scr WHERE scr.theatre_id = " + restrictToTheatreId + ")";
    }

    private jakarta.persistence.Query createQuery(String sql, FilterClause fc) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }
        return query;
    }

    // ==================== DASHBOARD KPIs ====================

    public DashboardResponse getDashboard(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS totalRevenue,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 0) AS ticketsSold,
                COUNT(DISTINCT s.id) AS totalShows,
                COUNT(DISTINCT t.id) AS totalTheatres,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS avgOccupancyPct
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql();

        Object[] row = (Object[]) createQuery(sql, fc).getSingleResult();

        BigDecimal totalRevenue = toBigDecimal(row[0]);
        long ticketsSold = toLong(row[1]);
        long totalShows = toLong(row[2]);
        long totalTheatres = toLong(row[3]);
        BigDecimal avgTicketPrice = toBigDecimal(row[4]);
        BigDecimal avgOccupancy = toBigDecimal(row[5]);

        BigDecimal revenuePerShow = totalShows > 0
            ? totalRevenue.divide(BigDecimal.valueOf(totalShows), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new DashboardResponse(
            totalRevenue, ticketsSold, totalShows, totalTheatres,
            avgTicketPrice, avgOccupancy, revenuePerShow
        );
    }

    // ==================== REVENUE TREND ====================

    public TimeSeriesResponse getRevenueTrend(Long restrictToTheatreId, AnalyticsFilter filter, String granularity) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String dateFormat;
        switch (granularity != null ? granularity.toLowerCase() : "daily") {
            case "weekly" -> dateFormat = "YEARWEEK(s.start_time, 1)";
            case "monthly" -> dateFormat = "DATE_FORMAT(s.start_time, '%Y-%m')";
            default -> dateFormat = "DATE(s.start_time)";
        }

        // Use string concat — NOT .formatted() on text block, which has operator precedence issues
        String sql = "SELECT "
            + dateFormat + " AS period, "
            + "COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue "
            + "FROM show_seats ss "
            + "JOIN shows s ON ss.show_id = s.id "
            + "JOIN screens scr ON s.screen_id = scr.id "
            + "JOIN theatres t ON scr.theatre_id = t.id "
            + "WHERE s.is_deleted = false "
            + "AND scr.is_deleted = false "
            + "AND t.is_deleted = false "
            + scope + fc.sql()
            + " GROUP BY period ORDER BY period";

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        for (Object[] row : rows) {
            labels.add(String.valueOf(row[0]));
            values.add(toBigDecimal(row[1]));
        }

        return new TimeSeriesResponse(labels, values);
    }

    // ==================== REVENUE BY DIMENSION ====================

    public DimensionBreakdownResponse getRevenueByDimension(Long restrictToTheatreId, AnalyticsFilter filter, String dimension) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        // Build SELECT, GROUP BY, and optional extra JOINs per dimension
        String dimSelect, dimGroup, extraJoin;
        switch (dimension.toLowerCase()) {
            case "movie" -> {
                dimSelect = "m.id, m.title";
                dimGroup = "m.id, m.title";
                extraJoin = "JOIN movies m ON s.movie_id = m.id AND m.is_deleted = false";
            }
            case "theatre" -> {
                dimSelect = "th.id, th.name";
                dimGroup = "th.id, th.name";
                extraJoin = "JOIN screens scr2 ON s.screen_id = scr2.id JOIN theatres th ON scr2.theatre_id = th.id AND th.is_deleted = false";
                scope = ""; // avoid double-join with base theatres t
            }
            case "screen" -> {
                dimSelect = "scr.id, scr.name";
                dimGroup = "scr.id, scr.name";
                extraJoin = "";
            }
            case "city" -> {
                dimSelect = "c.id, c.name";
                dimGroup = "c.id, c.name";
                extraJoin = "JOIN screens scr3 ON s.screen_id = scr3.id JOIN theatres t3 ON scr3.theatre_id = t3.id JOIN cities c ON t3.city_id = c.id";
                scope = "";
            }
            case "format" -> {
                dimSelect = "s.format AS dimId, s.format AS dimName";
                dimGroup = "s.format";
                extraJoin = "";
            }
            case "language" -> {
                dimSelect = "s.language AS dimId, s.language AS dimName";
                dimGroup = "s.language";
                extraJoin = "";
            }
            default -> throw new IllegalArgumentException("Unknown dimension: " + dimension);
        }

        // Plain string concat — avoids .formatted() operator precedence bug
        String sql = "SELECT "
            + dimSelect + ", "
            + "COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue, "
            + "SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold, "
            + "COUNT(DISTINCT s.id) AS showCount, "
            + "CASE WHEN COUNT(ss.id) > 0 "
            + "THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1) "
            + "ELSE 0 END AS occupancyPct, "
            + "CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0 "
            + "THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2) "
            + "ELSE 0 END AS avgTicketPrice "
            + "FROM show_seats ss "
            + "JOIN shows s ON ss.show_id = s.id "
            + "JOIN screens scr ON s.screen_id = scr.id "
            + "JOIN theatres t ON scr.theatre_id = t.id "
            + extraJoin + " "
            + "WHERE s.is_deleted = false "
            + "AND scr.is_deleted = false "
            + "AND t.is_deleted = false "
            + scope + fc.sql()
            + " GROUP BY " + dimGroup
            + " ORDER BY revenue DESC";

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<DimensionBreakdownItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new DimensionBreakdownItem(
                toLongOrNull(row[0]),
                String.valueOf(row[1]),
                toBigDecimal(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toBigDecimal(row[5]),
                toBigDecimal(row[6])
            ));
        }

        return new DimensionBreakdownResponse(items);
    }

    // ==================== MOVIE PERFORMANCE ====================

    public MoviePerformanceResponse getMoviePerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                m.id AS movieId,
                m.title AS title,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(DISTINCT s.id) AS showCount,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice,
                COALESCE((
                    SELECT t2.name FROM theatres t2
                    JOIN screens scr2 ON scr2.theatre_id = t2.id
                    JOIN shows s2 ON s2.screen_id = scr2.id
                    JOIN show_seats ss2 ON ss2.show_id = s2.id
                    WHERE s2.movie_id = m.id
                      AND ss2.status = 'BOOKED'
                      AND s2.is_deleted = false
                      AND t2.is_deleted = false
                    GROUP BY t2.id, t2.name
                    ORDER BY SUM(CASE WHEN ss2.status = 'BOOKED' THEN ss2.price ELSE 0 END) DESC
                    LIMIT 1
                ), 'N/A') AS bestTheatre
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            JOIN movies m ON s.movie_id = m.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              AND m.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY m.id, m.title
            ORDER BY revenue DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<MoviePerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new MoviePerformanceItem(
                toLong(row[0]),
                String.valueOf(row[1]),
                toBigDecimal(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toBigDecimal(row[5]),
                toBigDecimal(row[6]),
                row[7] != null ? String.valueOf(row[7]) : "N/A"
            ));
        }

        return new MoviePerformanceResponse(items);
    }

    // ==================== THEATRE PERFORMANCE ====================

    public TheatrePerformanceResponse getTheatrePerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                t.id AS theatreId,
                t.name AS name,
                c.name AS city,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(DISTINCT s.id) AS showCount,
                COUNT(DISTINCT scr.id) AS screenCount,
                COUNT(ss.id) AS totalCapacity,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS occupiedSeats,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                CASE WHEN COUNT(DISTINCT s.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / COUNT(DISTINCT s.id), 2)
                     ELSE 0 END AS revenuePerShow
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            JOIN cities c ON t.city_id = c.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              AND c.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY t.id, t.name, c.name
            ORDER BY revenue DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<TheatrePerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new TheatrePerformanceItem(
                toLong(row[0]),
                String.valueOf(row[1]),
                String.valueOf(row[2]),
                toBigDecimal(row[3]),
                toLong(row[4]),
                toLong(row[5]),
                toLong(row[6]),
                toLong(row[7]),
                toLong(row[8]),
                toBigDecimal(row[9]),
                toBigDecimal(row[10])
            ));
        }

        return new TheatrePerformanceResponse(items);
    }

    // ==================== SCREEN PERFORMANCE ====================

    public ScreenPerformanceResponse getScreenPerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                scr.id AS screenId,
                scr.name AS name,
                t.name AS theatreName,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(DISTINCT s.id) AS showCount,
                COUNT(ss.id) AS totalCapacity,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS occupiedSeats,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                CASE WHEN COUNT(DISTINCT s.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / COUNT(DISTINCT s.id), 2)
                     ELSE 0 END AS revenuePerShow,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY scr.id, scr.name, t.name
            ORDER BY revenue DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<ScreenPerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new ScreenPerformanceItem(
                toLong(row[0]),
                String.valueOf(row[1]),
                String.valueOf(row[2]),
                toBigDecimal(row[3]),
                toLong(row[4]),
                toLong(row[5]),
                toLong(row[6]),
                toLong(row[7]),
                toBigDecimal(row[8]),
                toBigDecimal(row[9]),
                toBigDecimal(row[10])
            ));
        }

        return new ScreenPerformanceResponse(items);
    }

    // ==================== SHOW PERFORMANCE ====================

    public ShowPerformanceResponse getShowPerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                s.id AS showId,
                m.title AS movieTitle,
                t.name AS theatreName,
                scr.name AS screenName,
                s.start_time AS startTime,
                s.format AS format,
                s.language AS language,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(ss.id) AS totalSeats,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            JOIN movies m ON s.movie_id = m.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              AND m.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY s.id, m.title, t.name, scr.name, s.start_time, s.format, s.language
            ORDER BY s.start_time DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<ShowPerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new ShowPerformanceItem(
                toLong(row[0]),
                String.valueOf(row[1]),
                String.valueOf(row[2]),
                String.valueOf(row[3]),
                toLocalDateTime(row[4]),
                String.valueOf(row[5]),
                String.valueOf(row[6]),
                toLong(row[7]),
                toLong(row[8]),
                toBigDecimal(row[9]),
                toBigDecimal(row[10]),
                toBigDecimal(row[11])
            ));
        }

        return new ShowPerformanceResponse(items);
    }

    // ==================== TIME-OF-DAY PERFORMANCE ====================

    public TimeSlotResponse getTimeSlotPerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                CASE
                    WHEN HOUR(s.start_time) >= 5 AND HOUR(s.start_time) < 12 THEN 'Morning (5AM-12PM)'
                    WHEN HOUR(s.start_time) >= 12 AND HOUR(s.start_time) < 17 THEN 'Afternoon (12PM-5PM)'
                    WHEN HOUR(s.start_time) >= 17 AND HOUR(s.start_time) < 21 THEN 'Evening (5PM-9PM)'
                    ELSE 'Night (9PM-5AM)'
                END AS slot,
                COUNT(DISTINCT s.id) AS showCount,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS avgOccupancyPct,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS totalRevenue
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY slot
            ORDER BY CASE slot
                WHEN 'Morning (5AM-12PM)' THEN 1
                WHEN 'Afternoon (12PM-5PM)' THEN 2
                WHEN 'Evening (5PM-9PM)' THEN 3
                ELSE 4
            END
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<TimeSlotPerformance> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new TimeSlotPerformance(
                String.valueOf(row[0]),
                toLong(row[1]),
                toBigDecimal(row[2]),
                toBigDecimal(row[3])
            ));
        }

        return new TimeSlotResponse(items);
    }

    // ==================== DAY-OF-WEEK PERFORMANCE ====================

    public DayOfWeekResponse getDayOfWeekPerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                DAYNAME(s.start_time) AS dayName,
                DAYOFWEEK(s.start_time) AS dayOrder,
                COUNT(DISTINCT s.id) AS showCount,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS avgOccupancyPct,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS totalRevenue
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY dayName, dayOrder
            ORDER BY dayOrder
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<DayOfWeekPerformance> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new DayOfWeekPerformance(
                String.valueOf(row[0]),
                (int) toLong(row[1]),
                toLong(row[2]),
                toBigDecimal(row[3]),
                toBigDecimal(row[4])
            ));
        }

        return new DayOfWeekResponse(items);
    }

    // ==================== FORMAT PERFORMANCE ====================

    public FormatPerformanceResponse getFormatPerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                s.format AS format,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(DISTINCT s.id) AS showCount,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY s.format
            ORDER BY revenue DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<FormatPerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new FormatPerformanceItem(
                String.valueOf(row[0]),
                toBigDecimal(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toBigDecimal(row[4]),
                toBigDecimal(row[5])
            ));
        }

        return new FormatPerformanceResponse(items);
    }

    // ==================== LANGUAGE PERFORMANCE ====================

    public LanguagePerformanceResponse getLanguagePerformance(Long restrictToTheatreId, AnalyticsFilter filter) {
        String scope = theatreScopeClause(restrictToTheatreId);
        FilterClause fc = buildFilter(filter, "s");

        String sql = """
            SELECT
                s.language AS language,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue,
                SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) AS ticketsSold,
                COUNT(DISTINCT s.id) AS showCount,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS occupancyPct,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
            """ + scope + fc.sql() + """
            GROUP BY s.language
            ORDER BY revenue DESC
            """;

        List<Object[]> rows = createQuery(sql, fc).getResultList();
        List<LanguagePerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new LanguagePerformanceItem(
                String.valueOf(row[0]),
                toBigDecimal(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toBigDecimal(row[4]),
                toBigDecimal(row[5])
            ));
        }

        return new LanguagePerformanceResponse(items);
    }

    // ==================== FILTER OPTIONS ====================

    /**
     * The single place that decides how a filter-option query is scoped. Both
     * fragments are trimmed and joined with an explicit newline, so a variant can
     * never be assembled into invalid SQL: the cities query previously inlined its
     * own copy of this fragment without a trailing space and produced
     * "... AND t.id = 1ORDER BY c.name", which failed for every theatre owner while
     * the unscoped (admin) path kept working. The theatre id is bound as a query
     * parameter rather than concatenated into the statement.
     */
    private static String filterOptionQuery(String head, String orderBy, boolean scoped) {
        StringBuilder sql = new StringBuilder(head.stripTrailing());
        if (scoped) {
            sql.append("\n  AND t.id = :theatreId");
        }
        return sql.append("\n").append(orderBy.strip()).toString();
    }

    private List<Object[]> runFilterOptionQuery(String sql, Long restrictToTheatreId) {
        jakarta.persistence.Query query = em.createNativeQuery(sql);
        if (restrictToTheatreId != null) {
            query.setParameter("theatreId", restrictToTheatreId);
        }
        return query.getResultList();
    }

    public FilterOptionsResponse getFilterOptions(Long restrictToTheatreId) {
        boolean scoped = restrictToTheatreId != null;

        String movieSql = filterOptionQuery("""
            SELECT DISTINCT m.id, m.title
            FROM movies m
            JOIN shows s ON s.movie_id = m.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE m.is_deleted = false AND s.is_deleted = false
              AND scr.is_deleted = false AND t.is_deleted = false
            """, "ORDER BY m.title", scoped);

        String theatreSql = filterOptionQuery("""
            SELECT DISTINCT t.id, t.name, t.city_id
            FROM theatres t
            JOIN screens scr ON scr.theatre_id = t.id
            JOIN shows s ON s.screen_id = scr.id
            WHERE t.is_deleted = false AND scr.is_deleted = false AND s.is_deleted = false
            """, "ORDER BY t.name", scoped);

        String screenSql = filterOptionQuery("""
            SELECT DISTINCT scr.id, scr.name, scr.theatre_id
            FROM screens scr
            JOIN shows s ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE scr.is_deleted = false AND s.is_deleted = false AND t.is_deleted = false
            """, "ORDER BY scr.name", scoped);

        String citySql = filterOptionQuery("""
            SELECT DISTINCT c.id, c.name
            FROM cities c
            JOIN theatres t ON t.city_id = c.id
            JOIN screens scr ON scr.theatre_id = t.id
            JOIN shows s ON s.screen_id = scr.id
            WHERE c.is_deleted = false AND t.is_deleted = false
              AND scr.is_deleted = false AND s.is_deleted = false
            """, "ORDER BY c.name", scoped);

        List<Object[]> movieRows = runFilterOptionQuery(movieSql, restrictToTheatreId);
        List<FilterOption> movies = movieRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1]), null))
            .toList();

        List<Object[]> theatreRows = runFilterOptionQuery(theatreSql, restrictToTheatreId);
        List<FilterOption> theatres = theatreRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1]),
                r[2] != null ? toLong(r[2]) : null))
            .toList();

        List<Object[]> screenRows = runFilterOptionQuery(screenSql, restrictToTheatreId);
        List<FilterOption> screens = screenRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1]),
                r[2] != null ? toLong(r[2]) : null))
            .toList();

        List<Object[]> cityRows = runFilterOptionQuery(citySql, restrictToTheatreId);
        List<FilterOption> cities = cityRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1]), null))
            .toList();

        return new FilterOptionsResponse(movies, theatres, screens, cities,
                formatOptions(), languageOptions(), null);
    }

    /**
     * The filter bar for a scope that has no data behind it yet, such as an owner
     * whose theatre has not been assigned. Format and language options mirror the
     * enums, so they are still offered; everything else is empty and the caller
     * supplies the explanation shown above the dropdowns.
     */
    public FilterOptionsResponse emptyScopeFilterOptions(String notice) {
        return new FilterOptionsResponse(List.of(), List.of(), List.of(), List.of(),
                formatOptions(), languageOptions(), notice);
    }

    static List<FilterOption> formatOptions() {
        return List.of(
            new FilterOption(null, "TWO_D", null),
            new FilterOption(null, "THREE_D", null),
            new FilterOption(null, "IMAX_2D", null),
            new FilterOption(null, "IMAX_3D", null),
            new FilterOption(null, "FOUR_DX", null)
        );
    }

    static List<FilterOption> languageOptions() {
        return List.of(
            new FilterOption(null, "ENGLISH", null),
            new FilterOption(null, "TAMIL", null),
            new FilterOption(null, "HINDI", null),
            new FilterOption(null, "TELUGU", null),
            new FilterOption(null, "KANNADA", null),
            new FilterOption(null, "MALAYALAM", null)
        );
    }

    // ==================== UTILITY ====================

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(val)); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(val)); }
        catch (Exception e) { return 0L; }
    }

    private static Long toLongOrNull(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(val)); }
        catch (Exception e) { return null; }
    }

    private static LocalDateTime toLocalDateTime(Object val) {
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        // Fallback: parse string, strip fractional seconds and replace space with T
        String s = String.valueOf(val).replaceAll("\\..*", "").replace(" ", "T");
        return LocalDateTime.parse(s);
    }
}
