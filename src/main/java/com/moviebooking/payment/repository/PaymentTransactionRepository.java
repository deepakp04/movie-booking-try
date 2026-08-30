package com.moviebooking.payment.repository;

import com.moviebooking.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<PaymentTransaction> findByBookingId(Long bookingId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.razorpayOrderId = :orderId")
    Optional<PaymentTransaction> findByRazorpayOrderIdForUpdate(String orderId);

    List<PaymentTransaction> findByStatusAndExpiresAtBefore(
        PaymentTransaction.PaymentStatus status, LocalDateTime expiresAt);
}
