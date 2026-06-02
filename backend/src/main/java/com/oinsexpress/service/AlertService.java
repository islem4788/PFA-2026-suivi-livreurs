package com.oinsexpress.service;

import com.oinsexpress.dto.TrackingDtos.*;
import com.oinsexpress.entity.*;
import com.oinsexpress.exception.NotFoundException;
import com.oinsexpress.repository.AlertRepository;
import com.oinsexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void createImuAnomalyAlert(PositionRequest req) {
        String livreurName = userRepository.findByLivreurId(req.getLivreurId())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse(req.getLivreurId());

        Alert alert = Alert.builder()
            .livreurId(req.getLivreurId())
            .livreurName(livreurName)
            .type(AlertType.IMU_ANOMALY)
            .message(String.format("Mouvement anormal détecté (acc=[%.1f, %.1f, %.1f])",
                req.getAccX(), req.getAccY(), req.getAccZ()))
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .severity(AlertSeverity.HIGH)
            .build();

        alertRepository.save(alert);
        broadcast(alert);
    }

    /**
     * Alerte conduite dangereuse (RISKY ou AGGRESSIVE) — déclenchée par Flask IA
     */
    @Transactional
    public void createDrivingStateAlert(String livreurId, double lat, double lon,
                                        String state) {
        String livreurName = userRepository.findByLivreurId(livreurId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse(livreurId);

        boolean isAggressive = "AGGRESSIVE".equals(state);

        Alert alert = Alert.builder()
            .livreurId(livreurId)
            .livreurName(livreurName)
            .type(AlertType.AGGRESSIVE_DRIVING)
            .message(isAggressive
                ? "Conduite agressive détectée — intervention requise"
                : "Conduite risquée détectée — vigilance recommandée")
            .latitude(lat)
            .longitude(lon)
            .severity(isAggressive ? AlertSeverity.HIGH : AlertSeverity.MEDIUM)
            .build();

        alertRepository.save(alert);
        broadcast(alert);
        log.info("[AlertService] Alerte {} créée pour livreur {}", state, livreurId);
    }

    @Transactional
    public void createStationaryAlert(String livreurId, double lat, double lon) {
        String livreurName = userRepository.findByLivreurId(livreurId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse(livreurId);

        Alert alert = Alert.builder()
            .livreurId(livreurId)
            .livreurName(livreurName)
            .type(AlertType.STATIONARY_TIMEOUT)
            .message("Livreur immobile depuis plus de 10 minutes")
            .latitude(lat)
            .longitude(lon)
            .severity(AlertSeverity.MEDIUM)
            .build();

        alertRepository.save(alert);
        broadcast(alert);
    }

    @Transactional
    public void createTrafficAlert(TrafficReportRequest req) {
        String livreurName = userRepository.findByLivreurId(req.getLivreurId())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse(req.getLivreurId());

        AlertType type = switch (req.getType()) {
            case ACCIDENT -> AlertType.ACCIDENT;
            default -> AlertType.TRAFFIC_REPORT;
        };

        AlertSeverity severity = req.getType() == TrafficType.ACCIDENT
            ? AlertSeverity.HIGH : AlertSeverity.LOW;

        Alert alert = Alert.builder()
            .livreurId(req.getLivreurId())
            .livreurName(livreurName)
            .type(type)
            .message(req.getType() + " : " + req.getDescription())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .severity(severity)
            .build();

        alertRepository.save(alert);
        broadcast(alert);
    }

    @Transactional
    public void createClientComplaintAlert(String packageId, String clientName, String comment) {
        Alert alert = Alert.builder()
            .livreurId("CLIENT")
            .livreurName(clientName)
            .type(AlertType.CLIENT_COMPLAINT)
            .message(String.format("Réclamation colis %s : %s", packageId,
                comment.length() > 200 ? comment.substring(0, 200) + "..." : comment))
            .latitude(0.0)
            .longitude(0.0)
            .severity(AlertSeverity.MEDIUM)
            .build();

        alertRepository.save(alert);
        broadcast(alert);
    }

    public List<AlertDto> getAlerts(boolean unresolvedOnly) {
        List<Alert> alerts = unresolvedOnly
            ? alertRepository.findByResolvedFalseOrderByCreatedAtDesc()
            : alertRepository.findAllByOrderByCreatedAtDesc();
        return alerts.stream().map(AlertDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public void resolveAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Alerte non trouvée"));
        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    private void broadcast(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts", AlertDto.fromEntity(alert));
    }
}
