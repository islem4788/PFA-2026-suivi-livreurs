package com.oinsexpress.dto;

import com.oinsexpress.entity.User;
import com.oinsexpress.entity.UserRole;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDto {
    private UUID id;
    private String livreurId;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String email;
    private UserRole role;
    private boolean emailVerified;
    private UUID bossId;
    private boolean active;
    private LocalDateTime createdAt;

    public static UserDto fromEntity(User u) {
        return UserDto.builder()
            .id(u.getId())
            .livreurId(u.getLivreurId())
            .firstName(u.getFirstName())
            .lastName(u.getLastName())
            .birthDate(u.getBirthDate())
            .email(u.getEmail())
            .role(u.getRole())
            .emailVerified(u.isEmailVerified())
            .bossId(u.getBossId())
            .active(u.isActive())
            .createdAt(u.getCreatedAt())
            .build();
    }
}
