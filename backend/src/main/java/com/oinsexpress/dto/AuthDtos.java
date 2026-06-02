package com.oinsexpress.dto;

import com.oinsexpress.entity.UserRole;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

public class AuthDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SignupRequest {
        private String livreurId;

        @NotBlank @Size(min = 2, max = 50)
        private String firstName;

        @NotBlank @Size(min = 2, max = 50)
        private String lastName;

        @NotNull @Past
        private LocalDate birthDate;

        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 100)
        private String password;

        @NotNull
        private UserRole role;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SigninRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuthResponse {
        private String token;
        private String refreshToken;
        private UserDto user;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerifyEmailRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Pattern(regexp = "^\\d{6}$")
        private String code;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ResendVerificationRequest {
        @NotBlank @Email
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ForgotPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Pattern(regexp = "^\\d{6}$")
        private String code;

        @NotBlank @Size(min = 8)
        private String newPassword;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SignupResponse {
        private String message;
        private String email;
        private String livreurId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageResponse {
        private String message;
    }
}
