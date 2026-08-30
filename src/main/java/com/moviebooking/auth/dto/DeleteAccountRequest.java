package com.moviebooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {

    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;
}