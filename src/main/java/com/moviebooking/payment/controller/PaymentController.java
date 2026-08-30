package com.moviebooking.payment.controller;

import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.payment.dto.CreateOrderRequest;
import com.moviebooking.payment.dto.OrderResponse;
import com.moviebooking.payment.dto.PaymentResponse;
import com.moviebooking.payment.dto.VerifyPaymentRequest;
import com.moviebooking.payment.service.PaymentService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

/**
 * Payment controller for Razorpay integration.
 * Handles order creation, payment verification, and webhook callbacks.
 */
@RestController
@RequestMapping("/api/payment")
@Secured({"ROLE_USER", "ROLE_ADMIN", "ROLE_THEATRE_OWNER"})
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a Razorpay order for a booking.
     * Client uses the returned order ID to open Razorpay Checkout.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@RequestBody CreateOrderRequest req) {
        log.info("Creating payment order for booking {}", req.bookingId());
        
        OrderResponse response = paymentService.createOrder(req);
        
        return ResponseEntity.ok(ApiResponse.success(
            "Payment order created successfully",
            response
        ));
    }

    /**
     * Verify payment after Razorpay checkout completion.
     * Client sends payment_id, order_id, and signature from Razorpay response.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(@RequestBody VerifyPaymentRequest req) {
        log.info("Verifying payment for order {}", req.razorpayOrderId());
        
        PaymentResponse response = paymentService.verifyPayment(req);
        
        return ResponseEntity.ok(ApiResponse.success(
            "Payment verified successfully",
            response
        ));
    }

    /**
     * Razorpay webhook endpoint.
     * Receives asynchronous payment status updates from Razorpay.
     * This is the authoritative source of truth for payment confirmation.
     *
     * The raw body is read for signature verification. The X-Razorpay-Signature
     * header is verified using HMAC-SHA256 with the Razorpay secret key.
     */
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawBody,
                                              @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay webhook");
        
        try {
            // Verify webhook signature before processing
            if (!paymentService.verifyWebhookSignature(rawBody, signature)) {
                log.error("Webhook signature verification failed. Rejecting webhook.");
                return ResponseEntity.badRequest().build();
            }
            
            JSONObject body = new JSONObject(rawBody);
            String orderId = body.getString("order_id");
            String paymentId = body.getString("payment_id");
            String event = body.getString("event");
            
            paymentService.handleWebhook(orderId, paymentId, signature, event);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            // Return 500 to signal Razorpay to retry
            return ResponseEntity.internalServerError().build();
        }
    }
}
