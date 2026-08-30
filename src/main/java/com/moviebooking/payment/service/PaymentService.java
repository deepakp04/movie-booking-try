package com.moviebooking.payment.service;

import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingStatus;
import com.moviebooking.booking.model.SeatStatus;
import com.moviebooking.booking.model.ShowSeat;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.CreateOrderRequest;
import com.moviebooking.payment.dto.OrderResponse;
import com.moviebooking.payment.dto.PaymentResponse;
import com.moviebooking.payment.dto.VerifyPaymentRequest;
import com.moviebooking.payment.model.PaymentTransaction;
import com.moviebooking.payment.repository.PaymentTransactionRepository;
import com.moviebooking.stream.dto.SeatUpdateEvent;
import com.moviebooking.stream.service.SeatStreamService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CURRENCY = "INR";

    @Value("${razorpay.key.id:rzp_test_XXXXXXXXXX}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:XXXXXXXXXX}")
    private String razorpayKeySecret;

    private final PaymentTransactionRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatStreamService seatStreamService;

    public PaymentService(PaymentTransactionRepository paymentRepository,
                         BookingRepository bookingRepository,
                         ShowSeatRepository showSeatRepository,
                         SeatStreamService seatStreamService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.seatStreamService = seatStreamService;
    }

    /**
     * Create a Razorpay order for a booking.
     * Validates that the booking hold is still active before allowing payment.
     * Extends the hold to 20 minutes from now to give user time to complete payment.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + req.bookingId()));

        // Validate booking status
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("This booking is not pending payment. Status: " + booking.getStatus());
        }

        // Check if hold has expired
        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt() != null && now.isAfter(booking.getHoldExpiresAt())) {
            // Expire the booking and release seats
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            
            // Broadcast seat releases
            String[] seatCodes = booking.getSeatCodes().split(",");
            for (String code : seatCodes) {
                seatStreamService.broadcastSeatUpdate(
                    booking.getShow().getId(),
                    new SeatUpdateEvent(booking.getShow().getId(), code, "AVAILABLE", null, null, "EXPIRED", null)
                );
            }
            
            throw new BusinessException("Session expired. Seats have been released. Please try booking again.");
        }

        // Check if payment transaction already exists
        PaymentTransaction existing = paymentRepository.findByBookingId(req.bookingId())
            .orElse(null);
        
        if (existing != null) {
            if (existing.getStatus() == PaymentTransaction.PaymentStatus.SUCCESS) {
                throw new BusinessException("Payment already completed for this booking.");
            }
            if (existing.getStatus() == PaymentTransaction.PaymentStatus.EXPIRED) {
                throw new BusinessException("Payment order expired. Please create a new order.");
            }
            // Return existing pending order
            return toOrderResponse(existing);
        }

        // Extend hold to 20 minutes from now to allow time for payment completion
        LocalDateTime newHoldExpiry = now.plusMinutes(20);
        booking.setHoldExpiresAt(newHoldExpiry);
        
        // Update all held seats with new expiry
        List<ShowSeat> heldSeats = showSeatRepository.findByBookingIdAndStatusForUpdate(
            booking.getId(), 
            SeatStatus.HELD
        );
        for (ShowSeat seat : heldSeats) {
            seat.setHoldExpiresAt(newHoldExpiry);
        }
        showSeatRepository.saveAll(heldSeats);
        bookingRepository.save(booking);
        
        log.info("Extended hold for booking {} to {}", booking.getId(), newHoldExpiry);

        // Create new payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(booking.getTransactionId());
        transaction.setBookingId(booking.getId());
        transaction.setAmount(booking.getTotalAmount());
        transaction.setCurrency(CURRENCY);
        transaction.setStatus(PaymentTransaction.PaymentStatus.PENDING);
        
        // Create a real Razorpay order via their REST API
        String razorpayOrderId = createRazorpayOrder(booking.getTotalAmount(), booking.getTransactionId());
        transaction.setRazorpayOrderId(razorpayOrderId);
        transaction.setExpiresAt(newHoldExpiry); // Match payment expiry to hold expiry
        
        PaymentTransaction saved = paymentRepository.save(transaction);
        
        log.info("Created payment order {} for booking {}", razorpayOrderId, booking.getId());
        
        return toOrderResponse(saved);
    }

    /**
     * Verify payment signature and confirm booking.
     * Uses HMAC-SHA256 to verify Razorpay's signature.
     */
    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest req) {
        PaymentTransaction transaction = paymentRepository.findByRazorpayOrderIdForUpdate(req.razorpayOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.razorpayOrderId()));

        // Verify signature
        boolean isValid = verifySignature(
            req.razorpayOrderId(),
            req.razorpayPaymentId(),
            req.razorpaySignature()
        );

        if (!isValid) {
            transaction.setStatus(PaymentTransaction.PaymentStatus.FAILED);
            transaction.setFailureReason("Invalid signature");
            paymentRepository.save(transaction);
            throw new BusinessException("Payment verification failed. Invalid signature.");
        }

        // Update transaction
        transaction.setRazorpayPaymentId(req.razorpayPaymentId());
        transaction.setRazorpaySignature(req.razorpaySignature());
        transaction.setStatus(PaymentTransaction.PaymentStatus.SUCCESS);
        transaction.setPaidAt(LocalDateTime.now());
        paymentRepository.save(transaction);

        // Confirm booking only if hold is still valid (Option A: fail-closed)
        Booking booking = bookingRepository.findById(transaction.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                && booking.getHoldExpiresAt() != null
                && LocalDateTime.now().isBefore(booking.getHoldExpiresAt())) {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            log.info("Payment verified successfully. Booking {} confirmed.", booking.getId());
            
            return new PaymentResponse(
                true,
                "Payment successful",
                transaction.getTransactionId(),
                req.razorpayPaymentId(),
                booking.getId(),
                booking.getTotalAmount(),
                transaction.getPaidAt()
            );
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // Already confirmed — idempotent response
            log.info("Payment verified but booking {} already confirmed.", booking.getId());
            
            return new PaymentResponse(
                true,
                "Payment already processed",
                transaction.getTransactionId(),
                req.razorpayPaymentId(),
                booking.getId(),
                booking.getTotalAmount(),
                transaction.getPaidAt()
            );
        } else {
            // Hold expired — payment arrived too late
            log.error("Payment verified for booking {} but hold expired. Payment must be refunded.",
                    booking.getId());
            throw new BusinessException(
                "Your seat hold expired before payment was completed. "
                + "The payment will be refunded automatically. Please try booking again.");
        }
    }

    /**
     * Handle Razorpay webhook callback.
     * Idempotent - safe to call multiple times.
     */
    @Transactional
    public void handleWebhook(String orderId, String paymentId, String signature, String event) {
        log.info("Received webhook for order {} event {}", orderId, event);
        
        PaymentTransaction transaction = paymentRepository.findByRazorpayOrderIdForUpdate(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Prevent double-processing
        if (transaction.getStatus() == PaymentTransaction.PaymentStatus.SUCCESS) {
            log.warn("Payment already confirmed for order {}", orderId);
            return;
        }

        // Verify signature
        boolean isValid = verifySignature(orderId, paymentId, signature);
        if (!isValid) {
            log.error("Webhook signature verification failed for order {}", orderId);
            return;
        }

        // Process based on event type
        if ("payment.captured".equals(event)) {
            transaction.setRazorpayPaymentId(paymentId);
            transaction.setRazorpaySignature(signature);
            transaction.setPaidAt(LocalDateTime.now());

            // Confirm booking only if seats are still held (Option A: fail-closed)
            Booking booking = bookingRepository.findById(transaction.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                    && booking.getHoldExpiresAt() != null
                    && LocalDateTime.now().isBefore(booking.getHoldExpiresAt())) {
                // Seats still held — safe to confirm
                transaction.setStatus(PaymentTransaction.PaymentStatus.SUCCESS);
                paymentRepository.save(transaction);
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Booking {} confirmed via webhook", booking.getId());
            } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
                // Already confirmed (idempotent — callback arrived first)
                transaction.setStatus(PaymentTransaction.PaymentStatus.SUCCESS);
                paymentRepository.save(transaction);
                log.warn("Booking {} already confirmed, webhook is idempotent", booking.getId());
            } else {
                // Hold expired or booking cancelled — refuse to confirm
                transaction.setStatus(PaymentTransaction.PaymentStatus.SUCCESS);
                transaction.setFailureReason("Payment arrived after hold expiry. Refund via Razorpay dashboard.");
                paymentRepository.save(transaction);
                log.error("Booking {} hold expired before webhook arrived. Payment captured but seats released."
                        + " Process refund via Razorpay dashboard.", booking.getId());
            }
        } else if ("payment.failed".equals(event)) {
            transaction.setStatus(PaymentTransaction.PaymentStatus.FAILED);
            transaction.setFailureReason("Payment failed via webhook");
            paymentRepository.save(transaction);
            
            // Release seats
            expireBooking(transaction.getBookingId());
        }
    }

    /**
     * Create a real Razorpay order by calling their REST API.
     * Returns the Razorpay order_id.
     */
    private String createRazorpayOrder(BigDecimal amount, String receipt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Amount must be in paise (smallest currency unit)
            long amountPaise = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
            
            JSONObject payload = new JSONObject();
            payload.put("amount", amountPaise);
            payload.put("currency", CURRENCY);
            payload.put("receipt", receipt);
            
            // Basic auth: base64(key_id:key_secret)
            String credentials = razorpayKeyId + ":" + razorpayKeySecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedCredentials);
            
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.razorpay.com/v1/orders",
                request,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject responseBody = new JSONObject(response.getBody());
                String orderId = responseBody.getString("id");
                log.info("Razorpay order created: {} for receipt: {}", orderId, receipt);
                return orderId;
            } else {
                throw new BusinessException("Failed to create Razorpay order: HTTP " + response.getStatusCode());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating Razorpay order", e);
            throw new BusinessException("Unable to connect to payment gateway. Please try again.");
        }
    }

    /**
     * Verify HMAC-SHA256 signature from Razorpay.
     */
    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String data = orderId + "|" + paymentId;
            
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(
                razorpayKeySecret.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            sha256_HMAC.init(secret_key);
            
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);
            
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    /**
     * Verify the X-Razorpay-Signature header for webhook requests.
     * The signature is computed over the raw request body using HMAC-SHA256.
     */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(
                razorpayKeySecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Webhook signature verification error", e);
            return false;
        }
    }

    /**
     * Expire a booking and release seats.
     */
    private void expireBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElse(null);
        
        if (booking != null && booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            
            // Broadcast seat releases
            String[] seatCodes = booking.getSeatCodes().split(",");
            for (String code : seatCodes) {
                seatStreamService.broadcastSeatUpdate(
                    booking.getShow().getId(),
                    new SeatUpdateEvent(booking.getShow().getId(), code, "AVAILABLE", null, null, "EXPIRED", null)
                );
            }
            
            log.info("Booking {} expired, seats released", bookingId);
        }
    }

    /**
     * Expire payment transactions whose hold has lapsed.
     * Called by the scheduled sweep and opportunistically.
     */
    @Transactional
    public void expireStaleTransactions() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentTransaction> stale = paymentRepository
                .findByStatusAndExpiresAtBefore(PaymentTransaction.PaymentStatus.PENDING, now);

        for (PaymentTransaction tx : stale) {
            tx.setStatus(PaymentTransaction.PaymentStatus.EXPIRED);
            tx.setFailureReason("Payment order expired — hold lapsed");
            paymentRepository.save(tx);

            // Also expire the associated booking if still pending
            expireBooking(tx.getBookingId());
            log.info("Expired payment transaction {} for booking {}", tx.getTransactionId(), tx.getBookingId());
        }
    }

    private OrderResponse toOrderResponse(PaymentTransaction t) {
        return new OrderResponse(
            t.getRazorpayOrderId(),
            t.getTransactionId(),
            t.getBookingId(),
            t.getAmount(),
            t.getCurrency(),
            razorpayKeyId,
            t.getCreatedAt(),
            t.getExpiresAt()
        );
    }
}
