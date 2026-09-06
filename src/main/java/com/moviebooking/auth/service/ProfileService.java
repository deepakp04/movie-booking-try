package com.moviebooking.auth.service;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.auth.dto.ProfileDTOs.GetProfileResponse;
import com.moviebooking.auth.dto.ProfileDTOs.UpdateProfileRequest;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;

@Service
public class ProfileService {

    private static final int MIN_AGE = 5;
    private static final int MAX_AGE = 120;

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();

        // --- Name validation ---
        if (name.length() < 2) {
            throw new BusinessException("Name must contain at least 2 characters.");
        }

        // --- Email validation ---
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("Invalid email address.");
        }

        // Check email uniqueness (excluding current user)
        if (!email.equals(user.getEmail().toLowerCase())
                && userRepository.existsByEmailAndIdNotAndIsDeletedFalse(email, user.getId())) {
            throw new BusinessException("Email address already in use. Please use a different email address.");
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

        // Apply changes
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDateOfBirth(dob);

        // If email changed, mark as unverified
        if (!email.equalsIgnoreCase(user.getEmail())) {
            user.setIsEmailVerified(false);
        }

        userRepository.save(user);

        return new GetProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth()
        );
    }
}
