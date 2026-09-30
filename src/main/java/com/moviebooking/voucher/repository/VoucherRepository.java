package com.moviebooking.voucher.repository;

import com.moviebooking.voucher.model.Voucher;
import com.moviebooking.voucher.model.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findTopByUserIdAndIsDeletedFalseOrderByIssuedAtDesc(Long userId);

    Optional<Voucher> findByCodeAndIsDeletedFalse(String code);

    /**
     * Locks the voucher row so two concurrent voucher bookings cannot spend the
     * same free tickets.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.code = :code AND v.isDeleted = false")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id AND v.isDeleted = false")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    List<Voucher> findByStatusAndIsDeletedFalse(VoucherStatus status);

    List<Voucher> findByStatusAndExpiresAtBeforeAndIsDeletedFalse(VoucherStatus status, LocalDateTime time);

    boolean existsByUserIdAndStatusAndIsDeletedFalse(Long userId, VoucherStatus status);

    boolean existsByCode(String code);

    List<Voucher> findAllByIsDeletedFalseOrderByIssuedAtDesc();
}
