package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.SeatTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatTierRepository extends JpaRepository<SeatTier, Long> {

    List<SeatTier> findByScreenIdAndIsDeletedFalseOrderByDisplayOrderAscIdAsc(Long screenId);

    Optional<SeatTier> findByIdAndIsDeletedFalse(Long id);

    boolean existsByScreenIdAndNameIgnoreCaseAndIsDeletedFalse(Long screenId, String name);
}
