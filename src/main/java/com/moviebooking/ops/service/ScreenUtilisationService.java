package com.moviebooking.ops.service;

import com.moviebooking.catalog.model.Movie;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ScreenUtilisationService {

    private static final Logger log = LoggerFactory.getLogger(ScreenUtilisationService.class);
    private static final int CLEANING_BUFFER_MINUTES = 30;
    private static final int DAILY_OPERATIONAL_HOURS = 13; // 10 AM to 11 PM
    private static final LocalTime OPERATIONS_START = LocalTime.of(10, 0);
    private static final LocalTime OPERATIONS_END = LocalTime.of(23, 0);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private final ShowRepository showRepository;
    private final ScreenRepository screenRepository;

    public ScreenUtilisationService(ShowRepository showRepository, ScreenRepository screenRepository) {
        this.showRepository = showRepository;
        this.screenRepository = screenRepository;
    }

    // ================= CONFLICT DETECTION =================

    public ConflictCheckResponse checkConflicts(Long screenId, LocalDateTime startTime, Integer durationMinutes) {
        Screen screen = screenRepository.findById(screenId)
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));

        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BusinessException("Duration must be a positive number");
        }

        LocalDateTime proposedStart = startTime;
        LocalDateTime proposedEnd = startTime.plusMinutes(durationMinutes + CLEANING_BUFFER_MINUTES);

        List<Show> overlapping = showRepository.findOverlappingShows(screenId, proposedStart, proposedEnd);

        List<ConflictDetail> conflicts = overlapping.stream()
                .map(show -> {
                    Movie movie = show.getMovie();
                    String endTime = show.getStartTime()
                            .plusMinutes(movie != null && movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 150)
                            .plusMinutes(CLEANING_BUFFER_MINUTES)
                            .format(DISPLAY_FMT);
                    return new ConflictDetail(
                            show.getId(),
                            movie != null ? movie.getTitle() : "Unknown",
                            show.getStartTime().format(DISPLAY_FMT),
                            endTime,
                            "Conflicts with '" + (movie != null ? movie.getTitle() : "Unknown") + "' ("
                                    + show.getStartTime().format(DISPLAY_FMT) + " - " + endTime + ")"
                    );
                })
                .toList();

        String message;
        if (conflicts.isEmpty()) {
            message = "✅ No conflicts. Screen '" + screen.getName() + "' is free from "
                    + proposedStart.format(DISPLAY_FMT) + " to " + proposedEnd.format(DISPLAY_FMT) + ".";
        } else {
            message = "❌ " + conflicts.size() + " conflict(s) found on '" + screen.getName() + "'.";
        }

        return new ConflictCheckResponse(
                !conflicts.isEmpty(),
                screenId,
                screen.getName(),
                proposedStart.format(DISPLAY_FMT),
                proposedEnd.format(DISPLAY_FMT),
                conflicts,
                message
        );
    }

    // ================= UTILISATION METRICS =================

    public UtilisationOverviewResponse getUtilisationOverview(Long theatreId, String dateFrom, String dateTo) {
        LocalDate fromDate = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDate.parse(dateFrom) : LocalDate.now().minusDays(7);
        LocalDate toDate = (dateTo != null && !dateTo.isBlank())
                ? LocalDate.parse(dateTo) : LocalDate.now();

        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(23, 59, 59);

        List<Screen> screens = screenRepository.findByTheatreIdAndIsDeletedFalse(theatreId);
        List<Show> shows = showRepository.findShowsByTheatreAndDateRange(theatreId, from, to);

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        double operationalHoursPerDay = DAILY_OPERATIONAL_HOURS;

        List<ScreenUtilisationRow> rows = new ArrayList<>();
        for (Screen screen : screens) {
            List<Show> screenShows = shows.stream()
                    .filter(s -> s.getScreen() != null && s.getScreen().getId().equals(screen.getId()))
                    .toList();

            double activeHours = screenShows.stream()
                    .mapToDouble(s -> {
                        Movie m = s.getMovie();
                        int dur = (m != null && m.getDurationMinutes() != null) ? m.getDurationMinutes() : 150;
                        return (dur + CLEANING_BUFFER_MINUTES) / 60.0;
                    })
                    .sum();

            double totalOperationalHours = totalDays * operationalHoursPerDay;
            double utilisation = totalOperationalHours > 0
                    ? Math.round((activeHours / totalOperationalHours) * 1000.0) / 10.0
                    : 0;

            String status;
            if (utilisation >= 60) status = "GREEN";
            else if (utilisation >= 30) status = "YELLOW";
            else status = "RED";

            rows.add(new ScreenUtilisationRow(
                    screen.getId(),
                    screen.getName(),
                    screen.getTotalSeats() != null ? screen.getTotalSeats() : 0,
                    (long) screenShows.size(),
                    Math.round(activeHours * 10.0) / 10.0,
                    totalOperationalHours,
                    utilisation,
                    status
            ));
        }

        // Sort by utilisation ascending (worst first)
        rows.sort((a, b) -> Double.compare(a.utilisationPercent(), b.utilisationPercent()));

        return new UtilisationOverviewResponse(
                theatreId,
                !screens.isEmpty() && screens.get(0).getTheatre() != null
                        ? screens.get(0).getTheatre().getName() : "",
                screens.size(),
                fromDate.toString(),
                toDate.toString(),
                rows
        );
    }
}
