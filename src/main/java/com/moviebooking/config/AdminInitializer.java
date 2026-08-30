package com.moviebooking.config;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository; // verify if UserRepository is in this package
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.UserStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@pvr.com";

        if (userRepository.findByEmailAndIsDeletedFalse(adminEmail).isEmpty()) {
            User admin = new User();
            admin.setName("Super Admin");
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
            admin.setRole(Role.ADMIN);
            admin.setIsEmailVerified(true);
            admin.setStatus(UserStatus.ACTIVE);

            userRepository.save(admin);
            System.out.println(">>> Default Admin User Created: " + adminEmail + " / Admin@123");
        }
    }
}