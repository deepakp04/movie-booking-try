package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByTheatreId(Long theatreId);

    List<Screen> findByTheatreIdAndIsDeletedFalse(Long theatreId);

    Optional<Screen> findByIdAndIsDeletedFalse(Long id);
}
