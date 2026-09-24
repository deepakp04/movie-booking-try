package com.moviebooking.analytics.dto;

import com.moviebooking.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All analytics request and response DTOs.
 * Backend returns clean data structures — Chart.js rendering happens in JS only.
 */
public final class AnalyticsDTOs {

    private AnalyticsDTOs() {}

    // ==================== REQUEST ====================

    public record AnalyticsFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long movieId,
        Long theatreId,
        Long screenId,
        Long cityId,
        String format,
        String language
    ) {
        /**
         * An impossible range is rejected up front, so every analytics endpoint
         * reports a readable message instead of quietly returning empty charts
         * when Date From is picked after Date To.
         */
        public AnalyticsFilter {
            if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
                throw new BusinessException("Start date (" + dateFrom
                        + ") cannot be after end date (" + dateTo + "). Pick a valid date range.");
            }
        }

        /** Defaults to last 30 days if dates are null. */
        public LocalDate effectiveDateFrom() {
            return dateFrom != null ? dateFrom : LocalDate.now().minusDays(30);
        }
        public LocalDate effectiveDateTo() {
            return dateTo != null ? dateTo : LocalDate.now();
        }
    }

    // ==================== DASHBOARD KPIs ====================

    public record DashboardResponse(
        BigDecimal totalRevenue,
        long totalTicketsSold,
        long totalShows,
        long totalTheatres,
        BigDecimal avgTicketPrice,
        BigDecimal avgOccupancyPct,
        BigDecimal revenuePerShow
    ) {}

    // ==================== TIME SERIES ====================

    public record TimeSeriesResponse(
        List<String> labels,
        List<BigDecimal> values
    ) {}

    // ==================== DIMENSION BREAKDOWN ====================

    public record DimensionBreakdownItem(
        Long id,
        String name,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        BigDecimal occupancyPct,
        BigDecimal avgTicketPrice
    ) {}

    public record DimensionBreakdownResponse(
        List<DimensionBreakdownItem> items
    ) {}

    // ==================== MOVIE PERFORMANCE ====================

    public record MoviePerformanceItem(
        Long movieId,
        String title,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        BigDecimal occupancyPct,
        BigDecimal avgTicketPrice,
        String bestTheatre
    ) {}

    public record MoviePerformanceResponse(
        List<MoviePerformanceItem> movies
    ) {}

    // ==================== THEATRE PERFORMANCE ====================

    public record TheatrePerformanceItem(
        Long theatreId,
        String name,
        String city,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        long screenCount,
        long totalCapacity,
        long occupiedSeats,
        BigDecimal occupancyPct,
        BigDecimal revenuePerShow
    ) {}

    public record TheatrePerformanceResponse(
        List<TheatrePerformanceItem> theatres
    ) {}

    // ==================== SCREEN PERFORMANCE ====================

    public record ScreenPerformanceItem(
        Long screenId,
        String name,
        String theatreName,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        long totalCapacity,
        long occupiedSeats,
        BigDecimal occupancyPct,
        BigDecimal revenuePerShow,
        BigDecimal avgTicketPrice
    ) {}

    public record ScreenPerformanceResponse(
        List<ScreenPerformanceItem> screens
    ) {}

    // ==================== SHOW PERFORMANCE ====================

    public record ShowPerformanceItem(
        Long showId,
        String movieTitle,
        String theatreName,
        String screenName,
        LocalDateTime startTime,
        String format,
        String language,
        long ticketsSold,
        long totalSeats,
        BigDecimal occupancyPct,
        BigDecimal revenue,
        BigDecimal avgTicketPrice
    ) {}

    public record ShowPerformanceResponse(
        List<ShowPerformanceItem> shows
    ) {}

    // ==================== TIME-OF-DAY / DAY-OF-WEEK ====================

    public record TimeSlotPerformance(
        String slot,
        long showCount,
        BigDecimal avgOccupancyPct,
        BigDecimal totalRevenue
    ) {}

    public record TimeSlotResponse(
        List<TimeSlotPerformance> slots
    ) {}

    public record DayOfWeekPerformance(
        String dayName,
        int dayOrder,
        long showCount,
        BigDecimal avgOccupancyPct,
        BigDecimal totalRevenue
    ) {}

    public record DayOfWeekResponse(
        List<DayOfWeekPerformance> days
    ) {}

    // ==================== FORMAT / LANGUAGE ====================

    public record FormatPerformanceItem(
        String format,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        BigDecimal occupancyPct,
        BigDecimal avgTicketPrice
    ) {}

    public record FormatPerformanceResponse(
        List<FormatPerformanceItem> formats
    ) {}

    public record LanguagePerformanceItem(
        String language,
        BigDecimal revenue,
        long ticketsSold,
        long showCount,
        BigDecimal occupancyPct,
        BigDecimal avgTicketPrice
    ) {}

    public record LanguagePerformanceResponse(
        List<LanguagePerformanceItem> languages
    ) {}

    // ==================== FILTER OPTIONS ====================

    public record FilterOption(
        Long id,
        String name,
        // Parent id used by the UI to cascade city -> theatre -> screen.
        // Null for options that have no parent (movies, formats, languages, cities).
        Long parentId
    ) {}

    public record FilterOptionsResponse(
        List<FilterOption> movies,
        List<FilterOption> theatres,
        List<FilterOption> screens,
        List<FilterOption> cities,
        List<FilterOption> formats,
        List<FilterOption> languages,
        String notice
    ) {}
}
