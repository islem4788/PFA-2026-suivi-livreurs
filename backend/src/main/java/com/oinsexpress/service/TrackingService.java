package com.oinsexpress.service;

import com.oinsexpress.dto.TrackingDtos.*;
import com.oinsexpress.entity.*;
import com.oinsexpress.repository.PositionRepository;
import com.oinsexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AlertService alertService;
    private final AnomalyDetectionService anomalyDetection;
    private final WebPushService webPushService;

   @Transactional
public void recordPosition(PositionRequest req) {

    AnomalyDetectionService.AnomalyResult anomaly =
        anomalyDetection.classify(req);

    DrivingState drivingState;
    try {
        drivingState = DrivingState.valueOf(anomaly.drivingState());
    } catch (Exception e) {
        drivingState = DrivingState.NORMAL;
    }

    Position position = Position.builder()
        .livreurId(req.getLivreurId())
        .latitude(req.getLatitude())
        .longitude(req.getLongitude())
        .speed(req.getSpeed())
        .heading(req.getHeading())
        .accuracy(req.getAccuracy())
        .drivingState(drivingState)
        .status(req.getStatus() != null ? req.getStatus() : LivreurStatus.ACTIVE)
        .accX(req.getAccX()).accY(req.getAccY()).accZ(req.getAccZ())
        .gyrX(req.getGyrX()).gyrY(req.getGyrY()).gyrZ(req.getGyrZ())
        .recordedAt(LocalDateTime.now())
        .build();

    positionRepository.save(position);

    if (anomaly.anomaly()) {
        alertService.createImuAnomalyAlert(req);

        // ✅ ENVOI PUSH NOTIFICATION FOND D'ECRAN
        String title = "AGGRESSIVE".equals(anomaly.drivingState())
            ? "🔴 Conduite dangereuse !"
            : "🟡 Conduite risquée";

        String body = String.format("Livreur %s", req.getLivreurId());

        webPushService.sendToAllBosses(title, body, "/boss/map");
    }

    broadcastPosition(position, req);
}

    public List<LivreurPositionDto> getAllLatestPositions() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Position> positions = positionRepository.findLatestPositions(since);

        Map<String, User> usersByLivreurId = userRepository.findAll().stream()
            .filter(u -> u.getLivreurId() != null)
            .collect(Collectors.toMap(User::getLivreurId, u -> u, (a, b) -> a));

        return positions.stream().map(p -> {
            User user = usersByLivreurId.get(p.getLivreurId());
            return LivreurPositionDto.builder()
                .livreurId(p.getLivreurId())
                .firstName(user != null ? user.getFirstName() : "Livreur")
                .lastName(user != null ? user.getLastName() : p.getLivreurId())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .speed(p.getSpeed() != null ? p.getSpeed() : 0.0)
                .heading(p.getHeading())
                .accuracy(p.getAccuracy())
                .drivingState(p.getDrivingState())
                .status(p.getStatus())
                .lastUpdate(p.getRecordedAt())
                .accX(p.getAccX()).accY(p.getAccY()).accZ(p.getAccZ())
                .gyrX(p.getGyrX()).gyrY(p.getGyrY()).gyrZ(p.getGyrZ())
                .build();
        }).collect(Collectors.toList());
    }

    /**
     * Retourne uniquement les positions des livreurs rattachés au boss donné.
     * Inclut TOUS les livreurs du boss, même ceux qui n'ont pas envoyé de position
     * récemment (avec status OFFLINE dans ce cas).
     */
    public List<LivreurPositionDto> getLatestPositionsForBoss(java.util.UUID bossId) {
        // Récupérer tous les livreurs rattachés à ce boss
        List<User> myLivreurs = userRepository.findByBossId(bossId);
        if (myLivreurs.isEmpty()) {
            return List.of();
        }

        // Indexer par livreurId pour un accès rapide
        Map<String, User> usersByLivreurId = myLivreurs.stream()
            .filter(u -> u.getLivreurId() != null)
            .collect(Collectors.toMap(User::getLivreurId, u -> u, (a, b) -> a));

        // Récupérer la dernière position de chaque livreur
        return usersByLivreurId.values().stream()
            .map(user -> {
                Optional<Position> latest = positionRepository
                    .findFirstByLivreurIdOrderByRecordedAtDesc(user.getLivreurId());

                if (latest.isPresent()) {
                    Position p = latest.get();
                    // Si la dernière position date de + de 5 min → OFFLINE
                    LivreurStatus status = p.getRecordedAt().isBefore(LocalDateTime.now().minusMinutes(5))
                        ? LivreurStatus.OFFLINE
                        : p.getStatus();

                    return LivreurPositionDto.builder()
                        .livreurId(p.getLivreurId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .speed(p.getSpeed() != null ? p.getSpeed() : 0.0)
                        .heading(p.getHeading())
                        .accuracy(p.getAccuracy())
                        .drivingState(p.getDrivingState())
                        .status(status)
                        .lastUpdate(p.getRecordedAt())
                        .accX(p.getAccX()).accY(p.getAccY()).accZ(p.getAccZ())
                        .gyrX(p.getGyrX()).gyrY(p.getGyrY()).gyrZ(p.getGyrZ())
                        .build();
                } else {
                    // Livreur rattaché mais jamais connecté
                    return LivreurPositionDto.builder()
                        .livreurId(user.getLivreurId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .latitude(0.0)
                        .longitude(0.0)
                        .speed(0.0)
                    .drivingState(DrivingState.NORMAL)
                    .status(LivreurStatus.OFFLINE)
                    .lastUpdate(null)
                    .accX(null).accY(null).accZ(null)
                    .gyrX(null).gyrY(null).gyrZ(null)
                    .build();
                }
            })
            .collect(Collectors.toList());
    }

    public Optional<LivreurPositionDto> getLivreurDetails(String livreurId) {
        return positionRepository.findFirstByLivreurIdOrderByRecordedAtDesc(livreurId)
            .map(p -> {
                User user = userRepository.findByLivreurId(livreurId).orElse(null);
                return LivreurPositionDto.builder()
                    .livreurId(p.getLivreurId())
                    .firstName(user != null ? user.getFirstName() : "Livreur")
                    .lastName(user != null ? user.getLastName() : livreurId)
                    .latitude(p.getLatitude())
                    .longitude(p.getLongitude())
                    .speed(p.getSpeed() != null ? p.getSpeed() : 0.0)
                    .heading(p.getHeading())
                    .accuracy(p.getAccuracy())
                    .drivingState(p.getDrivingState())
                    .status(p.getStatus())
                    .lastUpdate(p.getRecordedAt())
                    .accX(p.getAccX()).accY(p.getAccY()).accZ(p.getAccZ())
                    .gyrX(p.getGyrX()).gyrY(p.getGyrY()).gyrZ(p.getGyrZ())
                    .build();
            });
    }

    private void broadcastPosition(Position position, PositionRequest req) {
        LivreurPositionDto dto = LivreurPositionDto.builder()
            .livreurId(position.getLivreurId())
            .firstName(req.getFirstName())
            .lastName(req.getLastName())
            .latitude(position.getLatitude())
            .longitude(position.getLongitude())
            .speed(position.getSpeed() != null ? position.getSpeed() : 0.0)
            .heading(position.getHeading())
            .accuracy(position.getAccuracy())
            .drivingState(position.getDrivingState())
            .status(position.getStatus())
            .lastUpdate(position.getRecordedAt())
            .accX(position.getAccX()).accY(position.getAccY()).accZ(position.getAccZ())
            .gyrX(position.getGyrX()).gyrY(position.getGyrY()).gyrZ(position.getGyrZ())
            .build();

        messagingTemplate.convertAndSend("/topic/positions", dto);
    }
}
