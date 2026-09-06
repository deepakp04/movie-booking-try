package com.moviebooking.booking.repository;

import com.moviebooking.booking.model.BookingAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAttendeeRepository extends JpaRepository<BookingAttendee, Long> {

    List<BookingAttendee> findByBookingIdAndIsDeletedFalse(Long bookingId);
}
