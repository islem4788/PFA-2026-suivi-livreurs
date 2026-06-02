package com.oinsexpress.dto;

import com.oinsexpress.entity.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

public class TrackingDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PositionRequest {
        @NotBlank private String livreurId;
        private String firstName;
        private String lastName;

        @NotNull @DecimalMin("-90") @DecimalMax("90")
        private Double latitude;

        @NotNull @DecimalMin("-180") @DecimalMax("180")
        private Double longitude;

        private Double speed;
        private Double heading;
        private Double accuracy;
        private DrivingState drivingState;
        private LivreurStatus status;

        // IMU optionnel
        private Double accX, accY, accZ;
        private Double gyrX, gyrY, gyrZ;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LivreurPositionDto {
        private String livreurId;
        private String firstName;
        private String lastName;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
        private Double accuracy;
        private DrivingState drivingState;
        private LivreurStatus status;
        private LocalDateTime lastUpdate;
        private Double accX, accY, accZ;
        private Double gyrX, gyrY, gyrZ;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TrafficReportRequest {
        @NotBlank private String livreurId;
        @NotNull private TrafficType type;

        @NotBlank @Size(min = 10, max = 500)
        private String description;

        @NotNull private Double latitude;
        @NotNull private Double longitude;
    }

    public enum TrafficType {
        JAM, ACCIDENT, ROAD_BLOCKED, CONSTRUCTION
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AlertDto {
        private UUID id;
        private String livreurId;
        private String livreurName;
        private AlertType type;
        private String message;
        private Double latitude;
        private Double longitude;
        private AlertSeverity severity;
        private boolean resolved;
        private LocalDateTime createdAt;

        public static AlertDto fromEntity(Alert a) {
            return AlertDto.builder()
                .id(a.getId())
                .livreurId(a.getLivreurId())
                .livreurName(a.getLivreurName())
                .type(a.getType())
                .message(a.getMessage())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .severity(a.getSeverity())
                .resolved(a.isResolved())
                .createdAt(a.getCreatedAt())
                .build();
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ClientFeedbackRequest {
        @NotBlank private String packageId;
        @NotBlank @Size(min = 3) private String clientName;
        @NotBlank @Size(min = 5) private String clientAddress;
        @NotBlank @Size(min = 10) private String comment;
        @Min(1) @Max(5) private Integer rating;
    }
}
