package com.moviebooking.booking.repository;

import com.moviebooking.booking.model.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    long countByShowId(Long showId);

    List<ShowSeat> findByShowId(Long showId);

    // Row-level lock: two concurrent hold requests for overlapping seats will
    // serialize here instead of both succeeding.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.show.id = :showId and s.seatCode in :seatCodes")
    List<ShowSeat> findForUpdate(@Param("showId") Long showId, @Param("seatCodes") List<String> seatCodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.status = 'HELD' and s.holdExpiresAt < :now")
    List<ShowSeat> findExpiredHoldsForUpdate(@Param("now") LocalDateTime now);

    // ---- Phase 3: layout-lock support ----
    // A screen's layout may not be rewritten once any show on it has
    // materialized its seat map, because that show would otherwise keep selling
    // the old arrangement. Counting show_seats rows (not shows) is deliberate:
    // a show that has never been opened has no seats yet and does not block.
    @Query("SELECT COUNT(ss) FROM ShowSeat ss"
         + " WHERE ss.show.screen.id = :screenId"
         + " AND ss.show.isDeleted = false")
    long countMaterializedForScreen(@Param("screenId") Long screenId);

    @Query("SELECT DISTINCT CONCAT(ss.show.movie.title, ' at ', ss.show.startTime)"
         + " FROM ShowSeat ss"
         + " WHERE ss.show.screen.id = :screenId"
         + " AND ss.show.isDeleted = false")
    List<String> findMaterializedShowLabelsForScreen(@Param("screenId") Long screenId);

    long countByShowIdAndStatus(Long showId, com.moviebooking.booking.model.SeatStatus status);

    // Find held seats for a specific booking with lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.bookingId = :bookingId AND s.status = :status")
    List<ShowSeat> findByBookingIdAndStatusForUpdate(
        @Param("bookingId") Long bookingId,
        @Param("status") com.moviebooking.booking.model.SeatStatus status
    );
}
