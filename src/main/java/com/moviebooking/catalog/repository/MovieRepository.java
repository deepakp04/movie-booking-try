package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByIsDeletedFalseOrderByTitleAsc();

    Optional<Movie> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT DISTINCT s.movie FROM Show s"
         + " WHERE s.screen.theatre.city.id = :cityId"
         + " AND s.startTime >= CURRENT_TIMESTAMP"
         + " AND s.isDeleted = false"
         + " AND s.movie.isDeleted = false"
         + " AND s.screen.isDeleted = false"
         + " AND s.screen.theatre.isDeleted = false")
    List<Movie> findMoviesRunningInCity(@Param("cityId") Long cityId);
}
