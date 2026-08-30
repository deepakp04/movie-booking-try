package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.ShowTierPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShowTierPriceRepository extends JpaRepository<ShowTierPrice, Long> {

    List<ShowTierPrice> findByShowIdAndIsDeletedFalse(Long showId);

    Optional<ShowTierPrice> findByShowIdAndSeatTierIdAndIsDeletedFalse(Long showId, Long seatTierId);
}
