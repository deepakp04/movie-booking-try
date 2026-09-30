package com.moviebooking.booking.repository;

import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    /**
     * Customers whose lifetime *paid* spend crossed the threshold. Voucher-funded
     * bookings are excluded so free tickets never count towards re-earning a
     * voucher. Returns { user, spend, bookingCount, lastBookingAt }.
     */
    @Query("SELECT b.user, COALESCE(SUM(b.totalAmount), 0), COUNT(b), MAX(b.createdAt) "
            + "FROM Booking b WHERE b.status = :status AND b.voucher IS NULL AND b.isDeleted = false "
            + "GROUP BY b.user HAVING COALESCE(SUM(b.totalAmount), 0) > :threshold "
            + "ORDER BY COALESCE(SUM(b.totalAmount), 0) DESC")
    List<Object[]> findUsersWithPaidSpendAbove(@Param("status") BookingStatus status,
                                               @Param("threshold") BigDecimal threshold);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b "
            + "WHERE b.user.id = :userId AND b.status = :status AND b.voucher IS NULL AND b.isDeleted = false")
    BigDecimal sumPaidSpendForUser(@Param("userId") Long userId,
                                   @Param("status") BookingStatus status);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime time);
    
    // Admin/Owner queries with soft-delete filter
    @Query("SELECT b FROM Booking b WHERE b.show.screen.theatre.id = :theatreId AND b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Booking> findByTheatreId(@Param("theatreId") Long theatreId);
    
    @Query("SELECT b FROM Booking b WHERE b.show.id = :showId AND b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Booking> findByShowId(@Param("showId") Long showId);
    
    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Booking> findByStatus(@Param("status") BookingStatus status);
    
    @Query("SELECT b FROM Booking b WHERE b.isDeleted = false ORDER BY b.createdAt DESC")
    List<Booking> findAllByIsDeletedFalseOrderByCreatedAtDesc();
}
