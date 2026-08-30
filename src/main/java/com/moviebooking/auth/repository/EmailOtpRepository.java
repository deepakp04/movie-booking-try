package com.moviebooking.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.moviebooking.auth.entity.EmailOtp;
import com.moviebooking.auth.entity.User;
import com.moviebooking.common.constants.OtpPurpose;

public interface EmailOtpRepository
        extends JpaRepository<EmailOtp, Long> {
	
	Optional<EmailOtp> findTopByUserAndPurposeOrderByCreatedAtDesc(
	        User user,
	        OtpPurpose purpose
	);
	
	Optional<EmailOtp>
	findTopByUserAndPurposeOrderByIdDesc(
	        User user,
	        OtpPurpose purpose
	);
}