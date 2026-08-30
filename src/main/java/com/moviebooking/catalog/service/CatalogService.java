package com.moviebooking.catalog.service;

import com.moviebooking.catalog.dto.*;
import com.moviebooking.catalog.model.*;
import com.moviebooking.catalog.repository.*;
import com.moviebooking.booking.model.SeatStatus;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CityRepository cityRepository;
    private final TheatreRepository theatreRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public CatalogService(CityRepository cityRepository,
                          TheatreRepository theatreRepository,
                          MovieRepository movieRepository,
                          ShowRepository showRepository,
                          ShowSeatRepository showSeatRepository) {
        this.cityRepository = cityRepository;
        this.theatreRepository = theatreRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    public List<CityResponse> getAllCities() {
        return cityRepository.findByIsDeletedFalseOrderByNameAsc()
                .stream()
                .map(city -> new CityResponse(city.getId(), city.getName(), city.getState()))
                .collect(Collectors.toList());
    }

    public List<MovieSummaryResponse> getMoviesRunningInCity(Long cityId) {
        validateCityExists(cityId);
        List<Movie> movies = movieRepository.findMoviesRunningInCity(cityId);

        return movies.stream().map(movie -> {
            List<String> langs = showRepository.findAvailableLanguagesForMovieAndCity(movie.getId(), cityId)
                    .stream().map(Enum::name).collect(Collectors.toList());
            List<String> formats = showRepository.findAvailableFormatsForMovieAndCity(movie.getId(), cityId)
                    .stream().map(MovieFormat::getValue).collect(Collectors.toList());

            return new MovieSummaryResponse(
                    movie.getId(),
                    movie.getTitle(),
                    movie.getPosterUrl(),
                    movie.getDurationMinutes(),
                    movie.getCbfcRating(),
                    langs,
                    formats
            );
        }).collect(Collectors.toList());
    }

    public MovieDetailResponse getMovieDetails(Long movieId, Long cityId) {
        Movie movie = movieRepository.findByIdAndIsDeletedFalse(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + movieId));

        List<String> langs = showRepository.findAvailableLanguagesForMovieAndCity(movieId, cityId)
                .stream().map(Enum::name).collect(Collectors.toList());

        List<String> formats = showRepository.findAvailableFormatsForMovieAndCity(movieId, cityId)
                .stream().map(MovieFormat::getValue).collect(Collectors.toList());

        List<Show> shows = showRepository.findShowsForMovieAndCityInDateRange(
                movieId, cityId, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        boolean hasCaptions = shows.stream().anyMatch(Show::getHasCaptions);

        return new MovieDetailResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getCastMembers(),
                movie.getDirector(),
                movie.getDurationMinutes(),
                movie.getCbfcRating(),
                movie.getPosterUrl(),
                movie.getBannerUrl(),
                movie.getReleaseDate(),
                langs,
                formats,
                hasCaptions
        );
    }

    public List<TheatreShowResponse> getShowsForMovieAndDate(Long movieId, Long cityId, LocalDate date) {
        validateCityExists(cityId);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();

        // Only fetch shows that haven't started yet
        LocalDateTime effectiveStart = (startOfDay.isBefore(now)) ? now : startOfDay;

        List<Show> shows = showRepository.findShowsForMovieAndCityInDateRange(movieId, cityId, effectiveStart, endOfDay);

        // Group shows by Theatre
        Map<Theatre, List<Show>> theatreShowMap = shows.stream()
                .collect(Collectors.groupingBy(show -> show.getScreen().getTheatre()));

        List<TheatreShowResponse> response = new ArrayList<>();

        for (Map.Entry<Theatre, List<Show>> entry : theatreShowMap.entrySet()) {
            Theatre theatre = entry.getKey();
            List<ShowTimeResponse> showTimes = entry.getValue().stream()
                    .map(this::toShowTimeResponse).collect(Collectors.toList());

            response.add(new TheatreShowResponse(
                    theatre.getId(),
                    theatre.getName(),
                    theatre.getAddress(),
                    showTimes
            ));
        }

        return response;
    }

    private void validateCityExists(Long cityId) {
        if (cityRepository.findByIdAndIsDeletedFalse(cityId).isEmpty()) {
            throw new ResourceNotFoundException("City not found with ID: " + cityId);
        }
    }

    // Availability for one show. Seat maps are materialized lazily on first view,
    // so a show nobody has opened yet reports the screen's full capacity rather
    // than appearing sold out.
    private ShowTimeResponse toShowTimeResponse(Show show) {
        int capacity = show.getScreen().getTotalSeats() == null ? 0 : show.getScreen().getTotalSeats();
        long materialized = showSeatRepository.countByShowId(show.getId());

        int available = (materialized == 0)
                ? capacity
                : (int) showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE);
        int total = (materialized == 0) ? capacity : (int) materialized;

        return new ShowTimeResponse(
                show.getId(),
                show.getScreen().getName(),
                show.getStartTime(),
                show.getLanguage().name(),
                show.getFormat().getValue(),
                show.getHasCaptions(),
                show.getBasePrice(),
                total,
                available,
                available == 0
        );
    }

    /**
     * The date strip: which of the next N days actually have shows for this movie
     * in this city. Dates without shows are still returned so the UI can render
     * them in a different colour rather than hiding them.
     */
    public MovieDatesResponse getAvailableDates(Long movieId, Long cityId, int days) {
        validateCityExists(cityId);

        LocalDate today = LocalDate.now();
        int window = (days <= 0 || days > 30) ? 7 : days;

        List<Show> shows = showRepository.findShowsForMovieAndCityInDateRange(
                movieId, cityId,
                today.atStartOfDay(),
                today.plusDays(window - 1L).atTime(LocalTime.MAX));

        Map<LocalDate, Long> counts = shows.stream()
                .collect(Collectors.groupingBy(sh -> sh.getStartTime().toLocalDate(), Collectors.counting()));

        List<ShowDateResponse> dates = new ArrayList<>();
        LocalDate confirmedUntil = null;

        for (int i = 0; i < window; i++) {
            LocalDate d = today.plusDays(i);
            long c = counts.getOrDefault(d, 0L);
            dates.add(new ShowDateResponse(d, c > 0, (int) c));
            if (c > 0) {
                confirmedUntil = d;
            }
        }
        return new MovieDatesResponse(dates, confirmedUntil);
    }
}
