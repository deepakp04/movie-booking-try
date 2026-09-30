package com.moviebooking.voucher.controller;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.voucher.dto.VoucherDTOs.EligibleUserRow;
import com.moviebooking.voucher.dto.VoucherDTOs.IssueVoucherRequest;
import com.moviebooking.voucher.dto.VoucherDTOs.IssueVoucherResponse;
import com.moviebooking.voucher.dto.VoucherDTOs.VoucherListResponse;
import com.moviebooking.voucher.service.VoucherService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin portal — Vouchers tab.
 *
 * GET  /api/admin/vouchers            → voucher table + KPIs (status/search/completed filters)
 * GET  /api/admin/vouchers/eligible   → customers who crossed the spend threshold
 * POST /api/admin/vouchers/issue      → Approve/Send to the handpicked (or all) customers
 * POST /api/admin/vouchers/{id}/resend → resend the voucher email
 */
@RestController
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {

    private final VoucherService voucherService;
    private final UserRepository userRepository;

    public AdminVoucherController(VoucherService voucherService, UserRepository userRepository) {
        this.voucherService = voucherService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<VoucherListResponse> listVouchers(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "completed", defaultValue = "false") boolean completed) {
        return ApiResponse.success("Vouchers retrieved successfully",
                voucherService.listVouchers(status, search, completed));
    }

    @GetMapping("/eligible")
    public ApiResponse<List<EligibleUserRow>> listEligible(
            @RequestParam(value = "search", required = false) String search) {
        return ApiResponse.success("Eligible customers retrieved successfully",
                voucherService.listEligibleUsers(search));
    }

    @PostMapping("/issue")
    public ApiResponse<IssueVoucherResponse> issueVouchers(@RequestBody IssueVoucherRequest request,
                                                           HttpServletRequest httpRequest) {
        IssueVoucherResponse response = voucherService.issueVouchers(
                request != null ? request.userIds() : null,
                currentAdmin(),
                httpRequest.getRemoteAddr());

        String message = response.issued() == 0
                ? "No vouchers were sent — see the details for why."
                : response.issued() + " voucher" + (response.issued() == 1 ? "" : "s")
                    + " sent successfully" + (response.skipped() > 0 ? " (" + response.skipped() + " skipped)" : "")
                    + ".";
        return ApiResponse.success(message, response);
    }

    @PostMapping("/{voucherId}/resend")
    public ApiResponse<Void> resendVoucherEmail(@PathVariable("voucherId") Long voucherId,
                                                HttpServletRequest httpRequest) {
        voucherService.resendVoucherEmail(voucherId, currentAdmin(), httpRequest.getRemoteAddr());
        return ApiResponse.success("Voucher email queued for resend.", null);
    }

    private User currentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated admin not found"));
    }
}
