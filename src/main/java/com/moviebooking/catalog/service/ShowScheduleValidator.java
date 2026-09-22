package com.moviebooking.catalog.service;

import com.moviebooking.catalog.model.AudioLanguage;
import com.moviebooking.catalog.model.Movie;
import com.moviebooking.catalog.model.MovieFormat;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.repository.MovieRepository;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single place that decides whether a show may be scheduled.
 *
 * The admin portal and the owner portal both go through here so the two can
 * never drift apart, and so the API still rejects bad input when the browser
 * is bypassed. Every failure is a {@link BusinessException} carrying a message
 * that is safe to show directly to the user.
 */
@Component
public class ShowScheduleValidator {

    /** Minutes a screen needs between shows to be cleaned and reset. */
    public static final int CLEANING_BUFFER_MINUTES = 30;

    /** Guards against a mistyped year as much as against genuinely absurd input. */
    private static final int MAX_DAYS_AHEAD = 365;

    private static final int MAX_SEAT_CODE_LENGTH = 20;

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private final ScreenRepository screenRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;

    public ShowScheduleValidator(ScreenRepository screenRepository,
                                 MovieRepository movieRepository,
                                 ShowRepository showRepository) {
        this.screenRepository = screenRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
    }

    /** A scheduling request once it has been checked and normalised. */
    public record ValidatedSchedule(
            Screen screen,
            Movie movie,
            LocalDateTime startTime,
            AudioLanguage language,
            MovieFormat format,
            Boolean hasCaptions,
            BigDecimal basePrice,
            List<String> reservedSeatCodes
    ) {}

    public ValidatedSchedule validate(Long screenId,
                                      Long movieId,
                                      LocalDateTime startTime,
                                      AudioLanguage language,
                                      MovieFormat format,
                                      Boolean hasCaptions,
                                      BigDecimal basePrice,
                                      List<BigDecimal> tierPrices,
                                      List<String> reservedSeatCodes) {

        if (screenId == null) {
            throw new BusinessException("Select a screen before scheduling a show.");
        }
        if (movieId == null) {
            throw new BusinessException("Select a movie before scheduling a show.");
        }

        Screen screen = screenRepository.findByIdAndIsDeletedFalse(screenId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Screen not found with ID: " + screenId));
        Movie movie = movieRepository.findByIdAndIsDeletedFalse(movieId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Movie not found with ID: " + movieId));

        LocalDateTime now = LocalDateTime.now();

        if (startTime == null) {
            throw new BusinessException("Choose a start date and time for this show.");
        }
        if (startTime.isBefore(now)) {
            throw new BusinessException(
                    "A show cannot be scheduled in the past — "
                    + startTime.format(DISPLAY) + " has already passed. "
                    + "Pick a start time after " + now.format(DISPLAY) + ".");
        }
        if (startTime.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessException(
                    "That start time is more than a year away. Schedule shows within the next 12 months.");
        }

        if (language == null) {
            throw new BusinessException("Select an audio language for this show.");
        }
        if (format == null) {
            throw new BusinessException("Select a screening format for this show.");
        }

        if (screen.getTotalSeats() == null || screen.getTotalSeats() <= 0) {
            throw new BusinessException(
                    "Screen '" + screen.getName() + "' has no seat layout yet. "
                    + "Draw and save the seat layout in the Maintenance tab before scheduling shows on it.");
        }

        assertFormatAndLanguageOffered(movie, format, language);

        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Ticket price must be greater than zero.");
        }
        if (tierPrices != null) {
            for (BigDecimal price : tierPrices) {
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                            "Every seat tier needs a ticket price greater than zero.");
                }
            }
        }

        List<String> reserved = normalizeReservedSeats(screen, reservedSeatCodes);
        assertNoOverlap(screen, movie, startTime);

        return new ValidatedSchedule(screen, movie, startTime, language, format,
                hasCaptions != null ? hasCaptions : false, basePrice, reserved);
    }

    /**
     * Only enforced when the movie actually declares its formats / languages, so
     * older rows created before that metadata existed are not blocked.
     */
    private void assertFormatAndLanguageOffered(Movie movie, MovieFormat format, AudioLanguage language) {
        Set<MovieFormat> formats = movie.getAvailableFormats();
        if (formats != null && !formats.isEmpty() && !formats.contains(format)) {
            throw new BusinessException("'" + movie.getTitle() + "' is not offered in "
                    + format.getValue() + ". Available formats: " + describeFormats(formats) + ".");
        }

        Set<AudioLanguage> languages = movie.getAvailableLanguages();
        if (languages != null && !languages.isEmpty() && !languages.contains(language)) {
            throw new BusinessException("'" + movie.getTitle() + "' has no "
                    + language.name() + " audio track. Available languages: "
                    + describeLanguages(languages) + ".");
        }
    }

    private String describeFormats(Set<MovieFormat> formats) {
        return formats.stream().map(MovieFormat::getValue).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private String describeLanguages(Set<AudioLanguage> languages) {
        return languages.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
    }

    /**
     * Seat codes arrive from a free-text box, so tolerate stray commas, extra
     * spaces and lower casing — but reject genuine mistakes outright.
     */
    private List<String> normalizeReservedSeats(Screen screen, List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }

        Map<String, String> seen = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        List<String> tooLong = new ArrayList<>();

        for (String raw : rawCodes) {
            if (raw == null) {
                continue;
            }
            String code = raw.trim();
            if (code.isEmpty()) {
                continue;
            }
            String normalized = code.toUpperCase(Locale.ENGLISH);
            if (normalized.length() > MAX_SEAT_CODE_LENGTH) {
                tooLong.add(code);
                continue;
            }
            if (seen.putIfAbsent(normalized, normalized) != null) {
                duplicates.add(code);
            }
        }

        if (!tooLong.isEmpty()) {
            throw new BusinessException("These seat codes are not valid: "
                    + String.join(", ", tooLong) + ".");
        }
        if (!duplicates.isEmpty()) {
            throw new BusinessException("These seat codes are listed twice: "
                    + String.join(", ", duplicates) + ". Each reserved seat can only be listed once.");
        }

        List<String> codes = new ArrayList<>(seen.values());
        if (screen.getTotalSeats() != null && codes.size() > screen.getTotalSeats()) {
            throw new BusinessException("You reserved " + codes.size()
                    + " seats, but screen '" + screen.getName() + "' only has "
                    + screen.getTotalSeats() + ".");
        }
        return codes;
    }

    private void assertNoOverlap(Screen screen, Movie movie, LocalDateTime startTime) {
        LocalDateTime proposedEnd = endOf(startTime, movie);

        Show conflict = null;
        LocalDateTime earliestAllowed = null;

        for (Show other : showRepository.findByScreenIdAndIsDeletedFalse(screen.getId())) {
            LocalDateTime otherStart = other.getStartTime();
            if (otherStart == null) {
                continue;
            }
            LocalDateTime otherEnd = endOf(otherStart, other.getMovie());

            boolean overlaps = otherStart.isBefore(proposedEnd) && otherEnd.isAfter(startTime);
            if (!overlaps) {
                continue;
            }
            if (earliestAllowed == null || otherEnd.isAfter(earliestAllowed)) {
                earliestAllowed = otherEnd;
                conflict = other;
            }
        }

        if (conflict != null) {
            String title = conflict.getMovie() != null
                    ? conflict.getMovie().getTitle()
                    : "another movie";
            throw new BusinessException("Screen '" + screen.getName() + "' is already booked for '"
                    + title + "' from " + conflict.getStartTime().format(DISPLAY)
                    + " to " + endOf(conflict.getStartTime(), conflict.getMovie()).format(DISPLAY)
                    + ". The earliest this show can start is " + earliestAllowed.format(DISPLAY)
                    + " (includes a " + CLEANING_BUFFER_MINUTES + "-minute cleaning gap).");
        }
    }

    /** Start + runtime + cleaning buffer. */
    private LocalDateTime endOf(LocalDateTime start, Movie movie) {
        int minutes = movie != null && movie.getDurationMinutes() != null
                ? movie.getDurationMinutes()
                : 0;
        return start.plusMinutes(minutes + CLEANING_BUFFER_MINUTES);
    }
}
