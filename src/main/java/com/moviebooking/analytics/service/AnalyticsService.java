package com.moviebooking.analytics.service;

import com.moviebooking.analytics.dto.AnalyticsDTOs.*;
import com.moviebooking.analytics.repository.AnalyticsRepository;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics service.
 * Admin sees global data (restrictToTheatreId = null).
 * Owner sees only their theatre (resolved from JWT).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository repo;
    private final UserRepository userRepository;
    private final TheatreRepository theatreRepository;

    public AnalyticsService(AnalyticsRepository repo,
                            UserRepository userRepository,
                            TheatreRepository theatreRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.theatreRepository = theatreRepository;
    }

    /** Resolve the current user's role from the JWT. */
    private String getCurrentRole() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return "USER";
            var principal = auth.getPrincipal();
            if (principal instanceof User u) {
                return u.getRole() != null ? u.getRole().name() : "USER";
            }
            // Fallback: check if the name matches an admin
            String email = auth.getName();
            return userRepository.findByEmailAndIsDeletedFalse(email)
                .map(u -> u.getRole() != null ? u.getRole().name() : "USER")
                .orElse("USER");
        } catch (Exception e) {
            return "USER";
        }
    }

    /** Resolve theatre ID for owner, null for admin. */
    private Long resolveTheatreScope() {
        String role = getCurrentRole();
        if ("THEATRE_OWNER".equals(role)) {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
            Theatre theatre = theatreRepository.findByOwnerIdAndIsDeletedFalse(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No theatre assigned to your account"));
            return theatre.getId();
        }
        return null; // Admin sees everything
    }

    // ==================== DASHBOARD ====================

    public DashboardResponse getDashboard(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getDashboard(theatreId, filter);
    }

    // ==================== REVENUE TREND ====================

    public TimeSeriesResponse getRevenueTrend(AnalyticsFilter filter, String granularity) {
        Long theatreId = resolveTheatreScope();
        return repo.getRevenueTrend(theatreId, filter, granularity);
    }

    // ==================== REVENUE BY DIMENSION ====================

    public DimensionBreakdownResponse getRevenueByDimension(AnalyticsFilter filter, String dimension) {
        Long theatreId = resolveTheatreScope();
        return repo.getRevenueByDimension(theatreId, filter, dimension);
    }

    // ==================== MOVIE PERFORMANCE ====================

    public MoviePerformanceResponse getMoviePerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getMoviePerformance(theatreId, filter);
    }

    // ==================== THEATRE PERFORMANCE ====================

    public TheatrePerformanceResponse getTheatrePerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getTheatrePerformance(theatreId, filter);
    }

    // ==================== SCREEN PERFORMANCE ====================

    public ScreenPerformanceResponse getScreenPerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getScreenPerformance(theatreId, filter);
    }

    // ==================== SHOW PERFORMANCE ====================

    public ShowPerformanceResponse getShowPerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getShowPerformance(theatreId, filter);
    }

    // ==================== TIME SLOT ====================

    public TimeSlotResponse getTimeSlotPerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getTimeSlotPerformance(theatreId, filter);
    }

    // ==================== DAY OF WEEK ====================

    public DayOfWeekResponse getDayOfWeekPerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getDayOfWeekPerformance(theatreId, filter);
    }

    // ==================== FORMAT ====================

    public FormatPerformanceResponse getFormatPerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getFormatPerformance(theatreId, filter);
    }

    // ==================== LANGUAGE ====================

    public LanguagePerformanceResponse getLanguagePerformance(AnalyticsFilter filter) {
        Long theatreId = resolveTheatreScope();
        return repo.getLanguagePerformance(theatreId, filter);
    }

    // ==================== FILTER OPTIONS ====================

    public FilterOptionsResponse getFilterOptions() {
        try {
            return repo.getFilterOptions(resolveTheatreScope());
        } catch (ResourceNotFoundException e) {
            // An owner whose theatre assignment is missing still gets a usable filter
            // bar: the format and language options come from the enums rather than the
            // database, so they can be offered alongside an explanation. Throwing here
            // is what left the owner with empty dropdowns and only an error to show.
            return repo.emptyScopeFilterOptions(e.getMessage()
                    + " Analytics will stay empty until an admin assigns a theatre to your account.");
        }
    }
}
