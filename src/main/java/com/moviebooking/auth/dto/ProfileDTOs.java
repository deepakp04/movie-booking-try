package com.moviebooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ProfileDTOs {

    /**
     * Response returned by GET /auth/profile.
     */
    public record GetProfileResponse(
            String name,
            String email,
            String phone,
            LocalDate dateOfBirth
    ) {}

    /**
     * Request body for PUT /auth/profile.
     * All fields are required.
     */
    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required")
            @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
            @Pattern(
                    regexp = "^[\\p{L}\\p{M}\\s'\\-\\.]+$",
                    message = "Name contains unsupported characters. Use letters, spaces, hyphens, apostrophes, or periods."
            )
            String name,

            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email address. Enter a valid email, such as name@example.com.")
            @Size(max = 255, message = "Email must not exceed 255 characters")
            String email,

            @NotBlank(message = "Phone number is required")
            @Pattern(
                    regexp = "^\\+\\d{7,15}$",
                    message = "Invalid phone number. Enter a valid international number with country code, e.g. +919876543210."
            )
            String phone,

            @NotBlank(message = "Date of birth is required")
            String dateOfBirth
    ) {}

    // ------------------------------------------------------------------
    // Email change with OTP verification
    // ------------------------------------------------------------------

    /**
     * Step 1: Request OTP to change email.
     * Sends OTP to the CURRENT email address.
     */
    public record RequestEmailChangeRequest(
            @NotBlank(message = "New email is required")
            @Email(message = "Invalid email address. Enter a valid email, such as name@example.com.")
            @Size(max = 255, message = "Email must not exceed 255 characters")
            String newEmail
    ) {}

    /**
     * Step 2: Verify OTP and apply email change.
     */
    public record VerifyEmailChangeRequest(
            @NotBlank(message = "New email is required")
            @Email(message = "Invalid new email address.")
            String newEmail,

            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit number.")
            String otp
    ) {}

    // ------------------------------------------------------------------
    // Password change with OTP verification
    // ------------------------------------------------------------------

    /**
     * Step 1: Request OTP to change password.
     * Sends OTP to the current email address.
     * No body needed — user is identified from JWT.
     */
    public record RequestPasswordChangeRequest() {}

    /**
     * Step 2: Verify OTP and set new password.
     */
    public record VerifyPasswordChangeRequest(
            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit number.")
            String otp,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters.")
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,64}$",
                    message = "Password must contain at least 1 uppercase, 1 lowercase, 1 number, and 1 special character (@$!%*?&)."
            )
            String newPassword
    ) {}
}
