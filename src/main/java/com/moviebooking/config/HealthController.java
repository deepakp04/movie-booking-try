package com.moviebooking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.moviebooking.auth.dto.RegisterRequest;
import com.moviebooking.auth.entity.User;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.security.JwtService;

import jakarta.validation.Valid;

@RestController
public class HealthController {

    private static final Logger logger =
            LoggerFactory.getLogger(HealthController.class);
    
    private final JwtService jwtService;

    public HealthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {

        logger.info("Health endpoint accessed");

        return new ApiResponse<>(
                true,
                "Application Running",
                "Movie Booking Backend Active"
        );
    }
    
    @GetMapping("/test-error")
    public String testError() {
        throw new BusinessException("Testing global exception");
    }
    
    @PostMapping("/validate-test")
    public ApiResponse<String> validateTest(
            @Valid @RequestBody RegisterRequest request) {

        return new ApiResponse<>(
                true,
                "Validation Passed",
                "Success"
        );
    }
    
    @Autowired
    private BCryptPasswordEncoder encoder;

    @GetMapping("/encode")
    public String encode() {

        return encoder.encode("Password@123");
    }
    
    @GetMapping("/jwt-test")
    public String jwtTest() {

        User user = new User();

        user.setEmail("test@gmail.com");

        user.setRole(Role.USER);

        return jwtService.generateAccessToken(user);
    }
    
    @GetMapping("/secure-test")
    public String secureTest() {
        return "Protected Endpoint Working";
    }	
    
    @GetMapping("/me")
    public Object me(Authentication authentication) {

        return authentication.getName();
    }
}
