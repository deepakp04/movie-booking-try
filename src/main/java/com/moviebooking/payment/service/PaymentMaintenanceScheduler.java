package com.moviebooking.payment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentMaintenanceScheduler {

    private final PaymentService paymentService;

    public PaymentMaintenanceScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Expire stale payment transactions every 60 seconds, matching the
    // booking hold expiry sweep cadence.
    @Scheduled(fixedRate = 60000)
    public void expireStaleTransactions() {
        paymentService.expireStaleTransactions();
    }
}
