package com.oinsexpress.service;

import com.oinsexpress.dto.AuthDtos.*;
import com.oinsexpress.dto.UserDto;
import com.oinsexpress.entity.User;
import com.oinsexpress.exception.BadRequestException;
import com.oinsexpress.exception.ConflictException;
import com.oinsexpress.exception.ForbiddenException;
import com.oinsexpress.exception.UnauthorizedException;
import com.oinsexpress.repository.UserRepository;
import com.oinsexpress.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public SignupResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email déjà utilisé");
        }

        if (req.getLivreurId() != null && !req.getLivreurId().isBlank()) {
            if (userRepository.existsByLivreurId(req.getLivreurId())) {
                throw new ConflictException("ID livreur déjà utilisé");
            }
        }

        String code = generateCode();

        User user = User.builder()
            .livreurId(req.getLivreurId())
            .firstName(req.getFirstName())
            .lastName(req.getLastName())
            .birthDate(req.getBirthDate())
            .email(req.getEmail().toLowerCase())
            .password(passwordEncoder.encode(req.getPassword()))
            .role(req.getRole())
            .emailVerified(false)
            .verificationCode(code)
            .verificationCodeExpires(LocalDateTime.now().plusMinutes(15))
            .build();

        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getFirstName(), code);

        return SignupResponse.builder()
            .message("Compte créé. Vérifiez votre email.")
            .email(user.getEmail())
            .livreurId(user.getLivreurId())
            .build();
    }

    public AuthResponse signin(SigninRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase())
            .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }

        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Email non vérifié");
        }

        // Compte désactivé par le boss → accès refusé
        if (!user.isActive()) {
            throw new UnauthorizedException(
                "Votre compte a été désactivé. Contactez votre responsable."
            );
        }

        return AuthResponse.builder()
            .token(jwtService.generateToken(user))
            .refreshToken(jwtService.generateRefreshToken(user))
            .user(UserDto.fromEntity(user))
            .build();
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase())
            .orElseThrow(() -> new BadRequestException("Email non trouvé"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email déjà vérifié");
        }

        if (user.getVerificationCodeExpires() == null
            || user.getVerificationCodeExpires().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Code expiré");
        }

        if (!req.getCode().equals(user.getVerificationCode())) {
            throw new BadRequestException("Code incorrect");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpires(null);
        userRepository.save(user);

        return AuthResponse.builder()
            .token(jwtService.generateToken(user))
            .refreshToken(jwtService.generateRefreshToken(user))
            .user(UserDto.fromEntity(user))
            .build();
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
            .orElseThrow(() -> new BadRequestException("Email non trouvé"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email déjà vérifié");
        }

        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationCodeExpires(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendVerificationCode(user.getEmail(), user.getFirstName(), code);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
            .orElseThrow(() -> new BadRequestException("Email non trouvé"));

        String code = generateCode();
        user.setResetCode(code);
        user.setResetCodeExpires(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendResetPasswordCode(user.getEmail(), user.getFirstName(), code);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase())
            .orElseThrow(() -> new BadRequestException("Email non trouvé"));

        if (user.getResetCodeExpires() == null
            || user.getResetCodeExpires().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Code expiré");
        }

        if (!req.getCode().equals(user.getResetCode())) {
            throw new BadRequestException("Code incorrect");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setResetCode(null);
        user.setResetCodeExpires(null);
        userRepository.save(user);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1000000));
    }
}
