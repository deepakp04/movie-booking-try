package com.moviebooking.voucher.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flips lapsed ACTIVE vouchers to EXPIRED every hour. The same sweep also runs
 * lazily on reads (my-voucher, validation, admin list), so expiry is correct in
 * real time and the scheduler is only the backstop when nobody reads.
 */
@Component
public class VoucherMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(VoucherMaintenanceScheduler.class);

    private final VoucherService voucherService;

    public VoucherMaintenanceScheduler(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Scheduled(fixedRate = 3600000)
    public void expireLapsedVouchers() {
        try {
            voucherService.expireLapsedVouchers();
        } catch (Exception e) {
            log.warn("Voucher expiry sweep failed: {}", e.getMessage());
        }
    }
}
