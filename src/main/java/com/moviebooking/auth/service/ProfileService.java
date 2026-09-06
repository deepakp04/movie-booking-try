package com.moviebooking.auth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.auth.dto.ProfileDTOs.GetProfileResponse;
import com.moviebooking.auth.dto.ProfileDTOs.RequestEmailChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.RequestPasswordChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.UpdateProfileRequest;
import com.moviebooking.auth.dto.ProfileDTOs.VerifyEmailChangeRequest;
import com.moviebooking.auth.dto.ProfileDTOs.VerifyPasswordChangeRequest;
import com.moviebooking.auth.entity.EmailOtp;
import com.moviebooking.auth.entity.RefreshToken;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.EmailOtpRepository;
import com.moviebooking.auth.repository.RefreshTokenRepository;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.constants.OtpPurpose;
import com.moviebooking.common.constants.SecurityConstants;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.common.util.OtpGenerator;
import com.moviebooking.mail.EmailService;

@Service
public class ProfileService {

    private static final int MIN_AGE = 5;
    private static final int MAX_AGE = 120;

    private final UserRepository userRepository;
    private final EmailOtpRepository otpRepository;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BCryptPasswordEncoder encoder;

    public ProfileService(UserRepository userRepository,
                          EmailOtpRepository otpRepository,
                          EmailService emailService,
                          RefreshTokenRepository refreshTokenRepository,
                          BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.encoder = encoder;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public GetProfileResponse getProfile() {
        User user = currentUser();
        return new GetProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth()
        );
    }

    @Transactional
    public GetProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUser();

        // Trim inputs
        String name = request.name().trim().replaceAll("\\s+", " ");
        String phone = request.phone().trim();

        // --- Name validation ---
        if (name.length() < 2) {
            throw new BusinessException("Name must contain at least 2 characters.");
        }

        // --- Email: reject if trying to change via profile update (must use OTP flow) ---
        String email = request.email().trim().toLowerCase();
        if (!email.equals(user.getEmail().toLowerCase())) {
            throw new BusinessException("To change your email, use the 'Change Email' flow with OTP verification.");
        }

        // --- Phone validation ---
        if (!phone.matches("^\\+\\d{7,15}$")) {
            throw new BusinessException("Invalid phone number. Enter a valid international number with country code, e.g. +919876543210.");
        }

        // Check phone uniqueness (excluding current user, skip if unchanged)
        String currentPhone = user.getPhone();
        if ((currentPhone == null || !phone.equals(currentPhone))
                && userRepository.existsByPhoneAndIdNotAndIsDeletedFalse(phone, user.getId())) {
            throw new BusinessException("Phone number already associated with another account.");
        }

        // --- DOB validation ---
        LocalDate dob;
        try {
            dob = LocalDate.parse(request.dateOfBirth());
        } catch (Exception e) {
            throw new BusinessException("Invalid date of birth format. Use YYYY-MM-DD.");
        }

        if (dob.isAfter(LocalDate.now())) {
            throw new BusinessException("Invalid date of birth. Date of birth cannot be in the future.");
        }

        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BusinessException("Invalid date of birth. Age must be between " + MIN_AGE + " and " + MAX_AGE + " years.");
        }

        // Apply changes (email stays the same — use OTP flow to change)
        user.setName(name);
        user.setPhone(phone);
        user.setDateOfBirth(dob);

        userRepository.save(user);

        return new GetProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth()
        );
    }

    // ------------------------------------------------------------------
    // Email change with OTP
    // ------------------------------------------------------------------

    @Transactional
    public void requestEmailChange(RequestEmailChangeRequest request) {
        User user = currentUser();
        String newEmail = request.newEmail().trim().toLowerCase();

        // Validate new email
        if (!newEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("Invalid email address.");
        }

        // Same email — no change needed
        if (newEmail.equals(user.getEmail().toLowerCase())) {
            throw new BusinessException("This is already your current email address.");
        }

        // Check uniqueness
        if (userRepository.existsByEmailAndIsDeletedFalse(newEmail)) {
            throw new BusinessException("Email address already in use. Please use a different email address.");
        }

        // Rate-limit: check cooldown
        EmailOtp latestOtp = otpRepository
                .findTopByUserAndPurposeOrderByIdDesc(user, OtpPurpose.EMAIL_CHANGE)
                .orElse(null);
        if (latestOtp != null
                && latestOtp.getSentAt()
                        .plusSeconds(SecurityConstants.OTP_RESEND_COOLDOWN_SECONDS)
                        .isAfter(LocalDateTime.now())) {
            throw new BusinessException("Please wait before requesting another OTP.");
        }

        // Generate and send OTP to CURRENT email
        String otp = OtpGenerator.generateOtp();
        EmailOtp emailOtp = new EmailOtp();
        emailOtp.setUser(user);
        emailOtp.setOtpCode(otp);
        emailOtp.setPurpose(OtpPurpose.EMAIL_CHANGE);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(SecurityConstants.OTP_EXPIRY_MINUTES));
        emailOtp.setSentAt(LocalDateTime.now());
        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Transactional
    public void verifyEmailChange(VerifyEmailChangeRequest request) {
        User user = currentUser();
        String newEmail = request.newEmail().trim().toLowerCase();

        // Find latest OTP
        EmailOtp otpRecord = otpRepository
                .findTopByUserAndPurposeOrderByIdDesc(user, OtpPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new BusinessException("OTP not found. Please request a new one."));

        if (otpRecord.getIsUsed()) {
            throw new BusinessException("OTP already used. Please request a new one.");
        }
        if (otpRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("OTP expired. Please request a new one.");
        }
        if (otpRecord.getAttemptCount() >= SecurityConstants.MAX_OTP_ATTEMPTS) {
            throw new BusinessException("Maximum OTP attempts exceeded. Please request a new one.");
        }
        if (!otpRecord.getOtpCode().equals(request.otp())) {
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
            otpRepository.save(otpRecord);
            throw new BusinessException("Invalid OTP. Please try again.");
        }

        // OTP verified — apply email change
        otpRecord.setIsUsed(true);
        otpRepository.save(otpRecord);

        // Re-check uniqueness (race condition guard)
        if (userRepository.existsByEmailAndIsDeletedFalse(newEmail)
                && !newEmail.equals(user.getEmail().toLowerCase())) {
            throw new BusinessException("Email address was taken by another user. Please choose a different one.");
        }

        user.setEmail(newEmail);
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // Password change with OTP
    // ------------------------------------------------------------------

    @Transactional
    public void requestPasswordChange(RequestPasswordChangeRequest request) {
        User user = currentUser();

        // Rate-limit
        EmailOtp latestOtp = otpRepository
                .findTopByUserAndPurposeOrderByIdDesc(user, OtpPurpose.PASSWORD_CHANGE)
                .orElse(null);
        if (latestOtp != null
                && latestOtp.getSentAt()
                        .plusSeconds(SecurityConstants.OTP_RESEND_COOLDOWN_SECONDS)
                        .isAfter(LocalDateTime.now())) {
            throw new BusinessException("Please wait before requesting another OTP.");
        }

        // Generate and send OTP to current email
        String otp = OtpGenerator.generateOtp();
        EmailOtp emailOtp = new EmailOtp();
        emailOtp.setUser(user);
        emailOtp.setOtpCode(otp);
        emailOtp.setPurpose(OtpPurpose.PASSWORD_CHANGE);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(SecurityConstants.OTP_EXPIRY_MINUTES));
        emailOtp.setSentAt(LocalDateTime.now());
        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Transactional
    public void verifyPasswordChange(VerifyPasswordChangeRequest request) {
        User user = currentUser();

        // Find latest OTP
        EmailOtp otpRecord = otpRepository
                .findTopByUserAndPurposeOrderByIdDesc(user, OtpPurpose.PASSWORD_CHANGE)
                .orElseThrow(() -> new BusinessException("OTP not found. Please request a new one."));

        if (otpRecord.getIsUsed()) {
            throw new BusinessException("OTP already used. Please request a new one.");
        }
        if (otpRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("OTP expired. Please request a new one.");
        }
        if (otpRecord.getAttemptCount() >= SecurityConstants.MAX_OTP_ATTEMPTS) {
            throw new BusinessException("Maximum OTP attempts exceeded. Please request a new one.");
        }
        if (!otpRecord.getOtpCode().equals(request.otp())) {
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
            otpRepository.save(otpRecord);
            throw new BusinessException("Invalid OTP. Please try again.");
        }

        // OTP verified — update password
        otpRecord.setIsUsed(true);
        otpRepository.save(otpRecord);

        user.setPasswordHash(encoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens (force re-login on all devices)
        List<RefreshToken> tokens = refreshTokenRepository
                .findByUserAndIsRevokedFalse(user);
        for (RefreshToken token : tokens) {
            token.setIsRevoked(true);
        }
        refreshTokenRepository.saveAll(tokens);
    }
}
