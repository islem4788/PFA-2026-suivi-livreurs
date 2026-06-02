package com.oinsexpress.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "positions", indexes = {
    @Index(name = "idx_position_livreur_time", columnList = "livreur_id, recorded_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "livreur_id", nullable = false)
    private String livreurId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column
    private Double speed;

    @Column
    private Double heading;

    @Column
    private Double accuracy;

    @Enumerated(EnumType.STRING)
    @Column(name = "driving_state")
    @Builder.Default
    private DrivingState drivingState = DrivingState.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private LivreurStatus status = LivreurStatus.ACTIVE;

    @Column(name = "acc_x") private Double accX;
    @Column(name = "acc_y") private Double accY;
    @Column(name = "acc_z") private Double accZ;
    @Column(name = "gyr_x") private Double gyrX;
    @Column(name = "gyr_y") private Double gyrY;
    @Column(name = "gyr_z") private Double gyrZ;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
