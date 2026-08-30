package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.ScreenSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreenSeatRepository extends JpaRepository<ScreenSeat, Long> {

    List<ScreenSeat> findByScreenIdAndIsDeletedFalseOrderByRowLabelAscColIndexAsc(Long screenId);

    Optional<ScreenSeat> findByIdAndIsDeletedFalse(Long id);

    long countByScreenIdAndIsDeletedFalse(Long screenId);

    @Query("SELECT COUNT(s) FROM ScreenSeat s"
         + " WHERE s.screen.id = :screenId"
         + " AND s.isDeleted = false"
         + " AND s.seatType = com.moviebooking.catalog.model.SeatType.SEAT")
    long countSellableByScreenId(@Param("screenId") Long screenId);

    @Query("SELECT COUNT(s) FROM ScreenSeat s"
         + " WHERE s.seatTier.id = :tierId AND s.isDeleted = false")
    long countByTierId(@Param("tierId") Long tierId);

    // Layout replacement is a wholesale operation: the designer submits the
    // entire grid, so old rows are physically removed rather than soft deleted.
    // Soft deleting would accumulate dead rows and break the
    // (screen_id, row_label, col_index) unique constraint on the next save.
    @Modifying
    @Query("DELETE FROM ScreenSeat s WHERE s.screen.id = :screenId")
    void hardDeleteByScreenId(@Param("screenId") Long screenId);
}
