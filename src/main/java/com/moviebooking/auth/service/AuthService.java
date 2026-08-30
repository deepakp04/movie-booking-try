package com.moviebooking.auth.service;

import com.moviebooking.auth.dto.DeleteAccountRequest;
import com.moviebooking.auth.dto.ForgotPasswordRequest;
import com.moviebooking.auth.dto.LoginRequest;
import com.moviebooking.auth.dto.LoginResponse;
import com.moviebooking.auth.dto.LogoutRequest;
import com.moviebooking.auth.dto.RefreshTokenRequest;
import com.moviebooking.auth.dto.RegisterRequest;
import com.moviebooking.auth.dto.RegisterResponse;
import com.moviebooking.auth.dto.RequestAccountDeletionRequest;
import com.moviebooking.auth.dto.ResendOtpRequest;
import com.moviebooking.auth.dto.ResetPasswordRequest;
import com.moviebooking.auth.dto.VerifyLoginOtpRequest;
import com.moviebooking.auth.dto.VerifyOtpRequest;

public interface AuthService {

    RegisterResponse register(
            RegisterRequest request);
    void verifyRegistrationOtp(
            VerifyOtpRequest request
    );
    void resendRegistrationOtp(
            ResendOtpRequest request);
    void login(LoginRequest request);

    LoginResponse verifyLoginOtp(
            VerifyLoginOtpRequest request
    );
    
    LoginResponse refreshToken(
            RefreshTokenRequest request
    );
    
    void logout(LogoutRequest request);
    
    void logoutAllDevices(
            String email
    );
    
    void forgotPassword(
            ForgotPasswordRequest request);
    
    void resetPassword(
            ResetPasswordRequest request);
    
    void requestAccountDeletion(
            RequestAccountDeletionRequest request
    );
    
    void deleteAccount(
            DeleteAccountRequest request
    );
}