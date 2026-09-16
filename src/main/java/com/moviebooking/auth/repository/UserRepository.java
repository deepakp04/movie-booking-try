package com.moviebooking.auth.repository;

import com.moviebooking.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

	Optional<User> findByEmailAndIsDeletedFalse(
	        String email);

	boolean existsByEmailAndIsDeletedFalse(
	        String email);

	boolean existsByEmailAndIdNotAndIsDeletedFalse(
	        String email, Long id);

	boolean existsByPhoneAndIdNotAndIsDeletedFalse(
	        String phone, Long id);

	@Query("SELECT u FROM User u WHERE u.isDeleted = false AND (" +
	       "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
	       "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
	       "u.phone LIKE CONCAT('%', :query, '%')) ORDER BY u.name ASC")
	List<User> searchByNameEmailOrPhone(@Param("query") String query);

	@Query("SELECT DISTINCT u FROM User u JOIN Booking b ON b.user = u " +
	       "WHERE b.id = :bookingId AND b.isDeleted = false AND u.isDeleted = false")
	Optional<User> findByBookingId(@Param("bookingId") Long bookingId);
}