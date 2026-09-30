package com.moviebooking.voucher.controller;

import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.voucher.dto.VoucherDTOs.MyVoucherResponse;
import com.moviebooking.voucher.dto.VoucherDTOs.VoucherValidationRequest;
import com.moviebooking.voucher.dto.VoucherDTOs.VoucherValidationResponse;
import com.moviebooking.voucher.service.VoucherService;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing voucher endpoints used by the landing-page marquee, the info
 * box, and the voucher box on the seat-selection page.
 */
@RestController
@RequestMapping("/api/voucher")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    /** Active voucher + remaining free tickets for the signed-in customer. */
    @GetMapping("/my")
    public ApiResponse<MyVoucherResponse> getMyVoucher() {
        return ApiResponse.success("Voucher information retrieved successfully",
                voucherService.getMyVoucherSummary());
    }

    /**
     * Validates a code for the currently selected seat count, without consuming
     * anything. A count above the remaining free tickets is rejected with an
     * explanatory message the UI shows in its exception dialog.
     */
    @PostMapping("/validate")
    public ApiResponse<VoucherValidationResponse> validateVoucher(
            @RequestBody VoucherValidationRequest request) {
        VoucherValidationResponse response = voucherService.validateForBooking(
                request != null ? request.code() : null,
                request != null ? request.seats() : null);
        return ApiResponse.success(response.message(), response);
    }
}
