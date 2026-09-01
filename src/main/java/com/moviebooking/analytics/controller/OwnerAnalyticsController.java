package com.moviebooking.analytics.controller;

import com.moviebooking.analytics.dto.AnalyticsDTOs.*;
import com.moviebooking.analytics.service.AnalyticsService;
import com.moviebooking.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Owner analytics endpoints — auto-scoped to the owner's theatre via JWT.
 * Identical to admin endpoints but the service resolves theatre scope from the
 * authenticated owner identity, so the owner can never see another theatre's data.
 */
@RestController
@RequestMapping("/api/owner/analytics")
public class OwnerAnalyticsController {

    private final AnalyticsService analyticsService;

    public OwnerAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Dashboard retrieved",
            analyticsService.getDashboard(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/revenue")
    public ApiResponse<TimeSeriesResponse> getRevenueTrend(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "daily") String granularity) {

        return new ApiResponse<>(true, "Revenue trend retrieved",
            analyticsService.getRevenueTrend(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language), granularity));
    }

    @GetMapping("/revenue/breakdown")
    public ApiResponse<DimensionBreakdownResponse> getRevenueBreakdown(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "movie") String dimension) {

        return new ApiResponse<>(true, "Revenue breakdown retrieved",
            analyticsService.getRevenueByDimension(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language), dimension));
    }

    @GetMapping("/movies")
    public ApiResponse<MoviePerformanceResponse> getMoviePerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Movie performance retrieved",
            analyticsService.getMoviePerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/theatres")
    public ApiResponse<TheatrePerformanceResponse> getTheatrePerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Theatre performance retrieved",
            analyticsService.getTheatrePerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/screens")
    public ApiResponse<ScreenPerformanceResponse> getScreenPerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Screen performance retrieved",
            analyticsService.getScreenPerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/shows")
    public ApiResponse<ShowPerformanceResponse> getShowPerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Show performance retrieved",
            analyticsService.getShowPerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/timeslots")
    public ApiResponse<TimeSlotResponse> getTimeSlotPerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Time slot performance retrieved",
            analyticsService.getTimeSlotPerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/dayofweek")
    public ApiResponse<DayOfWeekResponse> getDayOfWeekPerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Day of week performance retrieved",
            analyticsService.getDayOfWeekPerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/formats")
    public ApiResponse<FormatPerformanceResponse> getFormatPerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Format performance retrieved",
            analyticsService.getFormatPerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/languages")
    public ApiResponse<LanguagePerformanceResponse> getLanguagePerformance(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String language) {

        return new ApiResponse<>(true, "Language performance retrieved",
            analyticsService.getLanguagePerformance(toFilter(dateFrom, dateTo, movieId, null, screenId, cityId, format, language)));
    }

    @GetMapping("/filters")
    public ApiResponse<FilterOptionsResponse> getFilterOptions() {
        return new ApiResponse<>(true, "Filter options retrieved",
            analyticsService.getFilterOptions());
    }

    // ==================== HELPER ====================

    private AnalyticsFilter toFilter(String dateFrom, String dateTo,
                                     Long movieId, Long theatreId, Long screenId,
                                     Long cityId, String format, String language) {
        java.time.LocalDate from = null;
        java.time.LocalDate to = null;
        try {
            if (dateFrom != null && !dateFrom.isBlank()) from = java.time.LocalDate.parse(dateFrom);
            if (dateTo != null && !dateTo.isBlank()) to = java.time.LocalDate.parse(dateTo);
        } catch (Exception ignored) {}

        return new AnalyticsFilter(from, to, movieId, theatreId, screenId, cityId, format, language);
    }
}
