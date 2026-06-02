package com.oinsexpress.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_feedback")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClientFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_address")
    private String clientAddress;

    @Column(length = 1000, nullable = false)
    private String comment;

    @Column
    private Integer rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
