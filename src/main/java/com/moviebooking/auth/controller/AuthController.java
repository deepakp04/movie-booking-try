package com.moviebooking.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moviebooking.auth.dto.DeleteAccountRequest;
import com.moviebooking.auth.dto.ForgotPasswordRequest;
import com.moviebooking.auth.dto.LoginRequest;
import com.moviebooking.auth.dto.LoginResponse;
import com.moviebooking.auth.dto.LogoutRequest;
import com.moviebooking.auth.dto.ProfileDTOs.GetProfileResponse;
import com.moviebooking.auth.dto.ProfileDTOs.RequestEmailChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.RequestPasswordChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.UpdateProfileRequest;
import com.moviebooking.auth.dto.ProfileDTOs.VerifyEmailChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.VerifyPasswordChangeRequest;
import com.moviebooking.auth.dto.RefreshTokenRequest;
import com.moviebooking.auth.dto.RegisterRequest;
import com.moviebooking.auth.dto.RegisterResponse;
import com.moviebooking.auth.dto.RequestAccountDeletionRequest;
import com.moviebooking.auth.dto.ResendOtpRequest;
import com.moviebooking.auth.dto.ResetPasswordRequest;
import com.moviebooking.auth.dto.VerifyLoginOtpRequest;
import com.moviebooking.auth.dto.VerifyOtpRequest;
import com.moviebooking.auth.service.AuthService;
import com.moviebooking.auth.service.ProfileService;
import com.moviebooking.common.response.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ProfileService profileService;

    public AuthController(AuthService authService, ProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response =
                authService.register(request);

        return new ApiResponse<>(
                true,
                "Registration successful",
                response
        );
    }
    
    @PostMapping("/verify-registration-otp")
    public ApiResponse<String>
    verifyRegistrationOtp(
            @Valid
            @RequestBody
            VerifyOtpRequest request) {

        authService
                .verifyRegistrationOtp(request);

        return new ApiResponse<>(
                true,
                "OTP verified successfully",
                "Account activated"
        );
    }
    
    @PostMapping("/resend-registration-otp")
    public ApiResponse<String>
    resendRegistrationOtp(
            @Valid
            @RequestBody
            ResendOtpRequest request) {

        authService
                .resendRegistrationOtp(request);

        return new ApiResponse<>(
                true,
                "OTP resent successfully",
                null
        );
    }
    
    @PostMapping("/login")
    public ApiResponse<String> login(
            @Valid @RequestBody
            LoginRequest request) {

        authService.login(request);

        return new ApiResponse<>(
                true,
                "Login OTP sent successfully",
                null
        );
    }
    
    @PostMapping("/verify-login-otp")
    public ApiResponse<LoginResponse>
    verifyLoginOtp(
            @Valid
            @RequestBody
            VerifyLoginOtpRequest request) {

        LoginResponse response =
                authService.verifyLoginOtp(
                        request);

        return new ApiResponse<>(
                true,
                "Login successful",
                response
        );
    }
    
    @PostMapping("/refresh-token")
    public ApiResponse<LoginResponse>
    refreshToken(
            @Valid
            @RequestBody
            RefreshTokenRequest request) {

        return new ApiResponse<>(
                true,
                "Token refreshed successfully",
                authService.refreshToken(request)
        );
    }
    
    @PostMapping("/logout")
    public ApiResponse<String> logout(
            @Valid
            @RequestBody
            LogoutRequest request) {

        authService.logout(request);

        return new ApiResponse<>(
                true,
                "Logout successful",
                null
        );
    }
    
    @PostMapping("/logout-all")
    public ApiResponse<String>
    logoutAll(
            Authentication authentication) {

        authService.logoutAllDevices(
                authentication.getName()
        );

        return new ApiResponse<>(
                true,
                "Logged out from all devices",
                null
        );
    }
    
    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(
            @Valid
            @RequestBody
            ForgotPasswordRequest request) {

        authService.forgotPassword(request);

        return new ApiResponse<>(
                true,
                "Password reset OTP sent successfully",
                null
        );
    }
    
    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(
            @Valid
            @RequestBody
            ResetPasswordRequest request) {

        authService.resetPassword(request);

        return new ApiResponse<>(
                true,
                "Password reset successful",
                null
        );
    }
    
    @PostMapping("/request-account-deletion")
    public ApiResponse<String> requestAccountDeletion(
            @Valid
            @RequestBody
            RequestAccountDeletionRequest request) {

        authService.requestAccountDeletion(
                request
        );

        return new ApiResponse<>(
                true,
                "Account deletion OTP sent successfully",
                null
        );
    }
    
    @PostMapping("/delete-account")
    public ApiResponse<String> deleteAccount(
            @Valid
            @RequestBody
            DeleteAccountRequest request) {

        authService.deleteAccount(
                request
        );

        return new ApiResponse<>(
                true,
                "Account deleted successfully",
                null
        );
    }

    // ------------------------------------------------------------------
    // Profile endpoints
    // ------------------------------------------------------------------

    @GetMapping("/profile")
    public ApiResponse<GetProfileResponse> getProfile() {
        GetProfileResponse response = profileService.getProfile();
        return new ApiResponse<>(true, "Profile retrieved successfully", response);
    }

    @PutMapping("/profile")
    public ApiResponse<GetProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        GetProfileResponse response = profileService.updateProfile(request);
        return new ApiResponse<>(true, "Profile updated successfully", response);
    }

    // ------------------------------------------------------------------
    // Email change with OTP
    // ------------------------------------------------------------------

    @PostMapping("/profile/request-email-change")
    public ApiResponse<String> requestEmailChange(
            @Valid @RequestBody RequestEmailChangeRequest request) {
        profileService.requestEmailChange(request);
        return new ApiResponse<>(true, "OTP sent to your current email address.", null);
    }

    @PostMapping("/profile/verify-email-change")
    public ApiResponse<String> verifyEmailChange(
            @Valid @RequestBody VerifyEmailChangeRequest request) {
        profileService.verifyEmailChange(request);
        return new ApiResponse<>(true, "Email address updated successfully.", null);
    }

    // ------------------------------------------------------------------
    // Password change with OTP
    // ------------------------------------------------------------------

    @PostMapping("/profile/request-password-change")
    public ApiResponse<String> requestPasswordChange(
            @Valid @RequestBody RequestPasswordChangeRequest request) {
        profileService.requestPasswordChange(request);
        return new ApiResponse<>(true, "OTP sent to your email address.", null);
    }

    @PostMapping("/profile/verify-password-change")
    public ApiResponse<String> verifyPasswordChange(
            @Valid @RequestBody VerifyPasswordChangeRequest request) {
        profileService.verifyPasswordChange(request);
        return new ApiResponse<>(true, "Password updated successfully. Please log in again on all devices.", null);
    }
}