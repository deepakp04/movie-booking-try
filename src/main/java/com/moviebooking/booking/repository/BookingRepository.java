package com.moviebooking.booking.repository;

import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
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
