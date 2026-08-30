package com.moviebooking.auth.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.moviebooking.audit.AuthLogService;
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
import com.moviebooking.auth.entity.EmailOtp;
import com.moviebooking.auth.entity.RefreshToken;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.EmailOtpRepository;
import com.moviebooking.auth.repository.RefreshTokenRepository;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.constants.AuthEventType;
import com.moviebooking.common.constants.OtpPurpose;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.SecurityConstants;
import com.moviebooking.common.constants.UserStatus;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.util.OtpGenerator;
import com.moviebooking.mail.EmailService;
import com.moviebooking.security.JwtService;

@Service
public class AuthServiceImpl
        implements AuthService {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder encoder;
    
    private final EmailOtpRepository otpRepository;

    private final EmailService emailService;
    
    private final AuthLogService authLogService;
    
    private final RefreshTokenRepository
    refreshTokenRepository;
    
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            BCryptPasswordEncoder encoder,EmailOtpRepository otpRepository,EmailService emailService,
            AuthLogService authLogService, RefreshTokenRepository
            refreshTokenRepository, JwtService jwtService) {

        this.userRepository = userRepository;
        this.encoder = encoder;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.authLogService = authLogService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;}

    @Override
    public RegisterResponse register(
            RegisterRequest request) {

        String email =
                request.getEmail()
                .trim()
                .toLowerCase();
        String cleanedName =
                request.getName().trim().replaceAll("\\s+", " ");
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
        	//TODO : Log the failed registration attempt
        	
            throw new BusinessException(
                    "Email already exists");
        }

        User user = new User();

        user.setName(cleanedName);

        user.setEmail(email);

        user.setPasswordHash(
                encoder.encode(
                        request.getPassword()));

        user.setRole(Role.USER);

        user.setStatus(
                UserStatus.PENDING_VERIFICATION);

        user.setIsEmailVerified(false);

        userRepository.save(user);
        
        authLogService.log(
                user,
                AuthEventType.REGISTER_SUCCESS
        );
        
        String otp =
                OtpGenerator.generateOtp();
        
        EmailOtp emailOtp =
                new EmailOtp();

        emailOtp.setUser(user);

        emailOtp.setOtpCode(otp);

        emailOtp.setPurpose(
                OtpPurpose.REGISTRATION
        );

        emailOtp.setExpiresAt(
                LocalDateTime.now().plusMinutes(10)
        );
        
        emailOtp.setSentAt(
                LocalDateTime.now()
        );

        
        otpRepository.save(emailOtp);
        
        emailService.sendOtpEmail(
                user.getEmail(),
                otp
        );
        

        return new RegisterResponse(
                user.getEmail(),
                "Registration successful. Verify OTP."
        );
    }
    
    @Override
    public void verifyRegistrationOtp(
            VerifyOtpRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository.findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"));

        EmailOtp otpRecord =
                otpRepository
                        .findTopByUserAndPurposeOrderByIdDesc(
                                user,
                                OtpPurpose.REGISTRATION)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "OTP not found"));

        if (otpRecord.getIsUsed()) {
            throw new BusinessException(
                    "OTP already used");
        }

        if (otpRecord.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new BusinessException(
                    "OTP expired");
        }
        
        if (otpRecord.getAttemptCount() >=
                SecurityConstants.MAX_OTP_ATTEMPTS) {

            throw new BusinessException(
                    "Maximum OTP attempts exceeded"
            );
        }

        if (!otpRecord.getOtpCode()
                .equals(request.getOtp())) {

            otpRecord.setAttemptCount(
                    otpRecord.getAttemptCount() + 1
            );

            otpRepository.save(otpRecord);
            
            authLogService.log(
                    user,
                    AuthEventType
                            .OTP_VERIFICATION_FAILED
            );

            throw new BusinessException(
                    "Invalid OTP");
        }

        otpRecord.setIsUsed(true);

        otpRepository.save(otpRecord);

        user.setIsEmailVerified(true);

        user.setStatus(UserStatus.ACTIVE);
        
        authLogService.log(
                user,
                AuthEventType.OTP_VERIFIED
        );

        userRepository.save(user);
    }
    
    @Override
    public void resendRegistrationOtp(
            ResendOtpRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository.findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"));

        if (user.getStatus() ==
                UserStatus.ACTIVE) {

            throw new BusinessException(
                    "Account already verified");
        }
        
        EmailOtp latestOtp =
                otpRepository
                        .findTopByUserAndPurposeOrderByIdDesc(
                                user,
                                OtpPurpose.REGISTRATION
                        )
                        .orElse(null);
        
        if (latestOtp != null &&
                latestOtp.getSentAt()
                        .plusSeconds(
                                SecurityConstants
                                        .OTP_RESEND_COOLDOWN_SECONDS
                        )
                        .isAfter(LocalDateTime.now())) {

            throw new BusinessException(
                    "Please wait before requesting another OTP"
            );
        }
        
        if (latestOtp != null &&
                latestOtp.getResendCount() >=
                        SecurityConstants.MAX_OTP_RESENDS) {

            throw new BusinessException(
                    "Maximum OTP resend limit reached"
            );
        }

        String otp =
                OtpGenerator.generateOtp();

        EmailOtp emailOtp =
                new EmailOtp();

        emailOtp.setUser(user);

        emailOtp.setOtpCode(otp);

        emailOtp.setPurpose(
                OtpPurpose.REGISTRATION
        );

        emailOtp.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(10)
        );
        
        emailOtp.setSentAt(
                LocalDateTime.now()
        );
        
        int resendCount = 0;

        if (latestOtp != null) {

            resendCount =
                    latestOtp.getResendCount() + 1;
        }
        
        emailOtp.setResendCount(
                resendCount
        );


        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(
                user.getEmail(),
                otp
        );
        
        authLogService.log(
                user,
                AuthEventType.OTP_RESENT
        );

    }
    @Override
    public void login(
            LoginRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository.findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Invalid credentials"));

        if (!encoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {

            authLogService.log(
                    user,
                    AuthEventType.LOGIN_FAILURE
            );

            throw new BusinessException(
                    "Invalid credentials");
        }

        if (!user.getIsEmailVerified()) {

            throw new BusinessException(
                    "Email not verified");
        }

        if (user.getStatus()
                != UserStatus.ACTIVE) {

            throw new BusinessException(
                    "Account not active");
        }

        String otp =
                OtpGenerator.generateOtp();

        EmailOtp emailOtp =
                new EmailOtp();

        emailOtp.setUser(user);

        emailOtp.setOtpCode(otp);

        emailOtp.setPurpose(
                OtpPurpose.LOGIN
        );

        emailOtp.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(10)
        );

        emailOtp.setSentAt(
                LocalDateTime.now()
        );

        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(
                user.getEmail(),
                otp
        );
    }
    
    
    @Override
    public LoginResponse verifyLoginOtp(VerifyLoginOtpRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        // --- DEV ADMIN BYPASS CHECK ---
        boolean isAdminBypass = user.getRole() == Role.ADMIN && "123456".equals(request.getOtp());

        if (!isAdminBypass) {
            EmailOtp otpRecord = otpRepository
                    .findTopByUserAndPurposeOrderByIdDesc(user, OtpPurpose.LOGIN)
                    .orElseThrow(() -> new BusinessException("OTP not found"));

            if (otpRecord.getIsUsed()) {
                throw new BusinessException("OTP already used");
            }

            if (otpRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException("OTP expired");
            }

            if (otpRecord.getAttemptCount() >= SecurityConstants.MAX_OTP_ATTEMPTS) {
                throw new BusinessException("Maximum OTP attempts exceeded");
            }

            if (!otpRecord.getOtpCode().equals(request.getOtp())) {
                otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
                otpRepository.save(otpRecord);

                authLogService.log(user, AuthEventType.OTP_VERIFICATION_FAILED);

                throw new BusinessException("Invalid OTP");
            }

            otpRecord.setIsUsed(true);
            otpRepository.save(otpRecord);
        }
        // --- END DEV ADMIN BYPASS CHECK ---

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshToken(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        refreshTokenRepository.save(refreshToken);

        authLogService.log(user, AuthEventType.LOGIN_SUCCESS);

        return new LoginResponse(accessToken, refreshTokenValue);
    }
    
    
    @Override
    public LoginResponse refreshToken(
            RefreshTokenRequest request) {

        RefreshToken tokenEntity =
                refreshTokenRepository
                        .findByToken(
                                request.getRefreshToken()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Invalid refresh token"));

        if (tokenEntity.getIsRevoked()) {

            throw new BusinessException(
                    "Refresh token revoked");
        }

        if (tokenEntity.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new BusinessException(
                    "Refresh token expired");
        }

        User user =
                tokenEntity.getUser();

        String accessToken =
                jwtService.generateAccessToken(user);

        String refreshToken =
                jwtService.generateRefreshToken(user);

        tokenEntity.setIsRevoked(true);

        refreshTokenRepository.save(tokenEntity);

        RefreshToken newToken =
                new RefreshToken();

        newToken.setUser(user);

        newToken.setToken(refreshToken);

        newToken.setExpiresAt(
                LocalDateTime.now().plusDays(7)
        );

        refreshTokenRepository.save(newToken);

        return new LoginResponse(
                accessToken,
                refreshToken
        );
    }
    @Override
    public void logout(
            LogoutRequest request) {

        RefreshToken token =
                refreshTokenRepository
                        .findByToken(
                                request.getRefreshToken()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Invalid refresh token"));

        token.setIsRevoked(true);

        refreshTokenRepository.save(token);
    }
    
    @Override
    public void logoutAllDevices(
            String email) {

        User user =
                userRepository.findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"));

        List<RefreshToken> tokens =
                refreshTokenRepository
                        .findByUserAndIsRevokedFalse(
                                user
                        );

        for (RefreshToken token : tokens) {

            token.setIsRevoked(true);
        }

        refreshTokenRepository.saveAll(tokens);
    }
    
    @Override
    public void forgotPassword(
            ForgotPasswordRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"));

        String otp =
                OtpGenerator.generateOtp();

        EmailOtp emailOtp =
                new EmailOtp();

        emailOtp.setUser(user);

        emailOtp.setOtpCode(otp);

        emailOtp.setPurpose(
                OtpPurpose.PASSWORD_RESET
        );

        emailOtp.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(
                                SecurityConstants
                                        .OTP_EXPIRY_MINUTES
                        )
        );

        emailOtp.setSentAt(
                LocalDateTime.now()
        );

        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(
                user.getEmail(),
                otp
        );
    }
    
    @Override
    public void resetPassword(
            ResetPasswordRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"
                                )
                        );

        EmailOtp otpRecord =
                otpRepository
                        .findTopByUserAndPurposeOrderByIdDesc(
                                user,
                                OtpPurpose.PASSWORD_RESET
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "OTP not found"
                                )
                        );

        if (otpRecord.getIsUsed()) {

            throw new BusinessException(
                    "OTP already used"
            );
        }

        if (otpRecord.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new BusinessException(
                    "OTP expired"
            );
        }

        if (otpRecord.getAttemptCount()
                >= SecurityConstants.MAX_OTP_ATTEMPTS) {

            throw new BusinessException(
                    "Maximum OTP attempts exceeded"
            );
        }

        if (!otpRecord.getOtpCode()
                .equals(request.getOtp())) {

            otpRecord.setAttemptCount(
                    otpRecord.getAttemptCount() + 1
            );

            otpRepository.save(otpRecord);

            authLogService.log(
                    user,
                    AuthEventType.OTP_VERIFICATION_FAILED
            );

            throw new BusinessException(
                    "Invalid OTP"
            );
        }

        user.setPasswordHash(
                encoder.encode(
                        request.getNewPassword()
                )
        );

        userRepository.save(user);

        otpRecord.setIsUsed(true);

        otpRepository.save(otpRecord);

        List<RefreshToken> activeTokens =
                refreshTokenRepository
                        .findByUserAndIsRevokedFalse(
                                user
                        );

        for (RefreshToken token : activeTokens) {

            token.setIsRevoked(true);
        }

        refreshTokenRepository.saveAll(
                activeTokens
        );

        authLogService.log(
                user,
                AuthEventType.PASSWORD_RESET
        );
    }
    
    @Override
    public void requestAccountDeletion(
            RequestAccountDeletionRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"
                                )
                        );

        if (user.getIsDeleted()) {

            throw new BusinessException(
                    "Account already deleted"
            );
        }

        String otp =
                OtpGenerator.generateOtp();

        EmailOtp emailOtp =
                new EmailOtp();

        emailOtp.setUser(user);

        emailOtp.setOtpCode(otp);

        emailOtp.setPurpose(
                OtpPurpose.ACCOUNT_DELETION
        );

        emailOtp.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(
                                SecurityConstants
                                        .OTP_EXPIRY_MINUTES
                        )
        );

        emailOtp.setSentAt(
                LocalDateTime.now()
        );

        otpRepository.save(emailOtp);

        emailService.sendOtpEmail(
                user.getEmail(),
                otp
        );

        authLogService.log(
                user,
                AuthEventType.OTP_SENT
        );
    }
    
    @Override
    public void deleteAccount(
            DeleteAccountRequest request) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "User not found"
                                )
                        );

        EmailOtp otpRecord =
                otpRepository
                        .findTopByUserAndPurposeOrderByIdDesc(
                                user,
                                OtpPurpose.ACCOUNT_DELETION
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "OTP not found"
                                )
                        );

        if (otpRecord.getIsUsed()) {

            throw new BusinessException(
                    "OTP already used"
            );
        }

        if (otpRecord.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new BusinessException(
                    "OTP expired"
            );
        }

        if (otpRecord.getAttemptCount()
                >= SecurityConstants.MAX_OTP_ATTEMPTS) {

            throw new BusinessException(
                    "Maximum OTP attempts exceeded"
            );
        }

        if (!otpRecord.getOtpCode()
                .equals(request.getOtp())) {

            otpRecord.setAttemptCount(
                    otpRecord.getAttemptCount() + 1
            );

            otpRepository.save(otpRecord);

            authLogService.log(
                    user,
                    AuthEventType.OTP_VERIFICATION_FAILED
            );

            throw new BusinessException(
                    "Invalid OTP"
            );
        }

        otpRecord.setIsUsed(true);

        otpRepository.save(otpRecord);

        user.setIsDeleted(true);

        user.setStatus(
                UserStatus.SUSPENDED
        );

        userRepository.save(user);

        List<RefreshToken> activeTokens =
                refreshTokenRepository
                        .findByUserAndIsRevokedFalse(
                                user
                        );

        for (RefreshToken token : activeTokens) {

            token.setIsRevoked(true);
        }

        refreshTokenRepository.saveAll(
                activeTokens
        );

        authLogService.log(
                user,
                AuthEventType.ACCOUNT_DELETION
        );
    }
}
