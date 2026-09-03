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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//"?"

/**
 * Native/JPQL aggregation queries for analytics.
 * All revenue comes from show_seats.price (immutable snapshot).
 * No entity loading — returns DTO projections only.
 */
@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager em;

    // ==================== SHARED FILTER CLAUSE ====================

    /**
     * Builds the common WHERE clause for filtering analytics queries.
     * All joins go through show → screen → theatre with isDeleted checks.
     * Revenue filter: show_seats.status = 'BOOKED'.
     *
     * The returned string starts with "WHERE" so callers append after a
     * suitable FROM ... JOIN clause.
     */
    private record FilterClause(String sql, List<Object> params) {}

    private FilterClause buildFilter(AnalyticsFilter f, String showAlias) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();

        LocalDate dateFrom = f.effectiveDateFrom();
        LocalDate dateTo = f.effectiveDateTo();
        LocalDateTime fromDt = dateFrom.atStartOfDay();
        LocalDateTime toDt = dateTo.plusDays(1).atStartOfDay(); // exclusive end

        where.append(" AND ").append(showAlias).append(".start_time >= ?").append(params.size() + 1);
        params.add(fromDt);
        where.append(" AND ").append(showAlias).append(".start_time < ?").append(params.size() + 1);
        params.add(toDt);

        if (f.movieId() != null) {
            where.append(" AND ").append(showAlias).append(".movie_id = ?").append(params.size() + 1);
            params.add(f.movieId());
        }
        if (f.theatreId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id IN (SELECT scr.id FROM screens scr WHERE scr.theatre_id = ?").append(params.size() + 1).append(")");
            params.add(f.theatreId());
        }
        if (f.screenId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id = ?").append(params.size() + 1);
            params.add(f.screenId());
        }
        if (f.cityId() != null) {
            where.append(" AND ").append(showAlias).append(".screen_id IN (SELECT scr.id FROM screens scr JOIN theatres t ON scr.theatre_id = t.id WHERE t.city_id = ?").append(params.size() + 1).append(")");
            params.add(f.cityId());
        }
        if (f.format() != null && !f.format().isBlank()) {
            where.append(" AND ").append(showAlias).append(".format = ?").append(params.size() + 1);
            params.add(f.format());
        }
        if (f.language() != null && !f.language().isBlank()) {
            where.append(" AND ").append(showAlias).append(".language = ?").append(params.size() + 1);
            params.add(f.language());
        }

        return new FilterClause(where.toString(), params);
    }

    /** Extra scoping clause for owner: restrict to their theatre. */
    private String theatreScopeClause(Long restrictToTheatreId) {
        if (restrictToTheatreId == null) return "";
        return " AND s.screen_id IN (SELECT scr.id FROM screens scr WHERE scr.theatre_id = " + restrictToTheatreId + ")";
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
                COUNT(DISTINCT s.screen_id) AS totalScreens,
                COUNT(DISTINCT t.id) AS totalTheatres,
                CASE WHEN SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END) / SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), 2)
                     ELSE 0 END AS avgTicketPrice,
                CASE WHEN COUNT(ss.id) > 0
                     THEN ROUND(SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END) * 100.0 / COUNT(ss.id), 1)
                     ELSE 0 END AS avgOccupancyPct,
                COUNT(DISTINCT s.id) AS totalShowsForRevenue
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              """ + scope + fc.sql();

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        Object[] row = (Object[]) query.getSingleResult();

        BigDecimal totalRevenue = toBigDecimal(row[0]);
        long ticketsSold = toLong(row[1]);
        long totalShows = toLong(row[2]);
        long totalTheatres = toLong(row[4]);
        BigDecimal avgTicketPrice = toBigDecimal(row[5]);
        BigDecimal avgOccupancy = toBigDecimal(row[6]);

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
            default -> dateFormat = "DATE(s.start_time)"; // daily
        }

        String sql = """
            SELECT
                %s AS period,
                COALESCE(SUM(CASE WHEN ss.status = 'BOOKED' THEN ss.price ELSE 0 END), 0) AS revenue
            FROM show_seats ss
            JOIN shows s ON ss.show_id = s.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              """ + scope + fc.sql() + """
            GROUP BY period
            ORDER BY period
            """.formatted(dateFormat);

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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

        String groupSelect, joinClause;
        switch (dimension.toLowerCase()) {
            case "movie" -> {
                groupSelect = "m.id, m.title";
                joinClause = "JOIN movies m ON s.movie_id = m.id AND m.is_deleted = false";
            }
            case "theatre" -> {
                groupSelect = "t.id, t.name";
                joinClause = "JOIN screens scr2 ON s.screen_id = scr2.id JOIN theatres t ON scr2.theatre_id = t.id AND t.is_deleted = false";
                // Remove the redundant theatre join from scope
                scope = "";
            }
            case "screen" -> {
                groupSelect = "scr.id, scr.name";
                joinClause = "";
            }
            case "city" -> {
                groupSelect = "c.id, c.name";
                joinClause = "JOIN screens scr3 ON s.screen_id = scr3.id JOIN theatres t3 ON scr3.theatre_id = t3.id JOIN cities c ON t3.city_id = c.id";
                scope = "";
            }
            case "format" -> {
                groupSelect = "CAST(s.format AS CHAR), s.format";
                joinClause = "";
            }
            case "language" -> {
                groupSelect = "CAST(s.language AS CHAR), s.language";
                joinClause = "";
            }
            default -> throw new IllegalArgumentException("Unknown dimension: " + dimension);
        }

        String sql = """
            SELECT
                %s AS dimId,
                %s AS dimName,
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
            %s
            WHERE s.is_deleted = false
              AND scr.is_deleted = false
              AND t.is_deleted = false
              """ + scope + fc.sql() + """
            GROUP BY %s, %s
            ORDER BY revenue DESC
            """.formatted(groupSelect, groupSelect, joinClause, groupSelect, groupSelect);

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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
                (SELECT t2.name FROM theatres t2
                 JOIN screens scr2 ON scr2.theatre_id = t2.id
                 JOIN shows s2 ON s2.screen_id = scr2.id
                 JOIN show_seats ss2 ON ss2.show_id = s2.id
                 WHERE s2.movie_id = m.id
                   AND ss2.status = 'BOOKED'
                   AND s2.is_deleted = false
                   AND t2.is_deleted = false
                 GROUP BY t2.id, t2.name
                 ORDER BY SUM(CASE WHEN ss2.status = 'BOOKED' THEN ss2.price ELSE 0 END) DESC
                 LIMIT 1) AS bestTheatre
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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
                CAST(s.format AS CHAR) AS format,
                CAST(s.language AS CHAR) AS language,
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
        List<ShowPerformanceItem> items = new ArrayList<>();

        for (Object[] row : rows) {
            items.add(new ShowPerformanceItem(
                toLong(row[0]),
                String.valueOf(row[1]),
                String.valueOf(row[2]),
                String.valueOf(row[3]),
                row[4] instanceof LocalDateTime ldt ? ldt : LocalDateTime.parse(String.valueOf(row[4])),
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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
                CAST(s.format AS CHAR) AS format,
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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
                CAST(s.language AS CHAR) AS language,
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

        var query = em.createNativeQuery(sql);
        for (int i = 0; i < fc.params().size(); i++) {
            query.setParameter(i + 1, fc.params().get(i));
        }

        List<Object[]> rows = query.getResultList();
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

    public FilterOptionsResponse getFilterOptions(Long restrictToTheatreId) {
        String theatreFilter = restrictToTheatreId != null
            ? " AND t.id = " + restrictToTheatreId
            : "";

        // Movies with confirmed shows
        String movieSql = """
            SELECT DISTINCT m.id, m.title
            FROM movies m
            JOIN shows s ON s.movie_id = m.id
            JOIN screens scr ON s.screen_id = scr.id
            JOIN theatres t ON scr.theatre_id = t.id
            WHERE m.is_deleted = false AND s.is_deleted = false
              AND scr.is_deleted = false AND t.is_deleted = false
              
              """ + theatreFilter + """
            ORDER BY m.title
            """;
        List<Object[]> movieRows = em.createNativeQuery(movieSql).getResultList();
        List<FilterOption> movies = movieRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1])))
            .toList();

        // Theatres
        String theatreSql = """
            SELECT DISTINCT t.id, t.name
            FROM theatres t
            JOIN screens scr ON scr.theatre_id = t.id
            JOIN shows s ON s.screen_id = scr.id
            WHERE t.is_deleted = false AND scr.is_deleted = false AND s.is_deleted = false
              
              """ + theatreFilter + """
            ORDER BY t.name
            """;
        List<Object[]> theatreRows = em.createNativeQuery(theatreSql).getResultList();
        List<FilterOption> theatres = theatreRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1])))
            .toList();

        // Screens
        String screenSql = """
            SELECT DISTINCT scr.id, scr.name
            FROM screens scr
            JOIN theatres t ON scr.theatre_id = t.id
            JOIN shows s ON s.screen_id = scr.id
            WHERE scr.is_deleted = false AND t.is_deleted = false AND s.is_deleted = false
              
              """ + theatreFilter + """
            ORDER BY scr.name
            """;
        List<Object[]> screenRows = em.createNativeQuery(screenSql).getResultList();
        List<FilterOption> screens = screenRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1])))
            .toList();

        // Cities
        String citySql = """
            SELECT DISTINCT c.id, c.name
            FROM cities c
            JOIN theatres t ON t.city_id = c.id
            JOIN screens scr ON scr.theatre_id = t.id
            JOIN shows s ON s.screen_id = scr.id
            WHERE c.is_deleted = false AND t.is_deleted = false
              AND scr.is_deleted = false AND s.is_deleted = false
              
              """ + (restrictToTheatreId != null
                  ? " AND t.id = " + restrictToTheatreId
                  : "") + """
            ORDER BY c.name
            """;
        List<Object[]> cityRows = em.createNativeQuery(citySql).getResultList();
        List<FilterOption> cities = cityRows.stream()
            .map(r -> new FilterOption(toLong(r[0]), String.valueOf(r[1])))
            .toList();

        // Formats (enum values)
        List<FilterOption> formats = List.of(
            new FilterOption(null, "TWO_D"),
            new FilterOption(null, "THREE_D"),
            new FilterOption(null, "IMAX_2D"),
            new FilterOption(null, "IMAX_3D"),
            new FilterOption(null, "FOUR_DX")
        );

        // Languages (enum values)
        List<FilterOption> languages = List.of(
            new FilterOption(null, "ENGLISH"),
            new FilterOption(null, "TAMIL"),
            new FilterOption(null, "HINDI"),
            new FilterOption(null, "TELUGU"),
            new FilterOption(null, "KANNADA"),
            new FilterOption(null, "MALAYALAM")
        );

        return new FilterOptionsResponse(movies, theatres, screens, cities, formats, languages);
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
}