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
}
