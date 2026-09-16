package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.AudioLanguage;
import com.moviebooking.catalog.model.MovieFormat;
import com.moviebooking.catalog.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByIsDeletedFalse();

    Optional<Show> findByIdAndIsDeletedFalse(Long id);

    List<Show> findByScreenIdAndIsDeletedFalse(Long screenId);

    boolean existsByScreenIdAndIsDeletedFalseAndStartTimeAfter(Long screenId, LocalDateTime after);

    boolean existsByScreenTheatreIdAndIsDeletedFalseAndStartTimeAfter(Long theatreId, LocalDateTime after);

    boolean existsByMovieIdAndIsDeletedFalseAndStartTimeAfter(Long movieId, LocalDateTime after);

    @Query("SELECT DISTINCT s.language FROM Show s"
         + " WHERE s.movie.id = :movieId"
         + " AND s.screen.theatre.city.id = :cityId"
         + " AND s.startTime >= CURRENT_TIMESTAMP"
         + " AND s.isDeleted = false"
         + " AND s.movie.isDeleted = false"
         + " AND s.screen.isDeleted = false"
         + " AND s.screen.theatre.isDeleted = false")
    List<AudioLanguage> findAvailableLanguagesForMovieAndCity(@Param("movieId") Long movieId,
                                                             @Param("cityId") Long cityId);

    @Query("SELECT DISTINCT s.format FROM Show s"
         + " WHERE s.movie.id = :movieId"
         + " AND s.screen.theatre.city.id = :cityId"
         + " AND s.startTime >= CURRENT_TIMESTAMP"
         + " AND s.isDeleted = false"
         + " AND s.movie.isDeleted = false"
         + " AND s.screen.isDeleted = false"
         + " AND s.screen.theatre.isDeleted = false")
    List<MovieFormat> findAvailableFormatsForMovieAndCity(@Param("movieId") Long movieId,
                                                          @Param("cityId") Long cityId);

    @Query("SELECT s FROM Show s"
         + " WHERE s.movie.id = :movieId"
         + " AND s.screen.theatre.city.id = :cityId"
         + " AND s.startTime BETWEEN :startTime AND :endTime"
         + " AND s.startTime > CURRENT_TIMESTAMP"
         + " AND s.isDeleted = false"
         + " AND s.movie.isDeleted = false"
         + " AND s.screen.isDeleted = false"
         + " AND s.screen.theatre.isDeleted = false"
         + " ORDER BY s.screen.theatre.name, s.startTime")
    List<Show> findShowsForMovieAndCityInDateRange(
            @Param("movieId") Long movieId,
            @Param("cityId") Long cityId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ---- Phase 4: scope filtering for the admin/owner show list ----
    // The list previously used findByIsDeletedFalse(), which surfaced last
    // month's completed shows alongside upcoming ones.
    List<Show> findByIsDeletedFalseAndStartTimeGreaterThanEqualOrderByStartTimeAsc(LocalDateTime from);

    List<Show> findByIsDeletedFalseAndStartTimeLessThanOrderByStartTimeDesc(LocalDateTime before);

    List<Show> findByIsDeletedFalseOrderByStartTimeDesc();

    // ---- Screen Utilisation: find overlapping shows for conflict detection ----
    @Query("SELECT s FROM Show s"
         + " WHERE s.screen.id = :screenId"
         + " AND s.isDeleted = false"
         + " AND s.startTime < :proposedEnd"
         + " AND s.startTime > :proposedStart"
         + " ORDER BY s.startTime")
    List<Show> findOverlappingShows(
            @Param("screenId") Long screenId,
            @Param("proposedStart") LocalDateTime proposedStart,
            @Param("proposedEnd") LocalDateTime proposedEnd
    );

    // ---- Screen Utilisation: all shows for a theatre in a date range ----
    @Query("SELECT s FROM Show s"
         + " WHERE s.screen.theatre.id = :theatreId"
         + " AND s.isDeleted = false"
         + " AND s.startTime BETWEEN :from AND :to"
         + " ORDER BY s.screen.id, s.startTime")
    List<Show> findShowsByTheatreAndDateRange(
            @Param("theatreId") Long theatreId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
