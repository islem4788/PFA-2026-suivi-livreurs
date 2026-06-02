package com.oinsexpress.controller;

import com.oinsexpress.dto.SensorDTO;
import com.oinsexpress.entity.DrivingState;
import com.oinsexpress.entity.Position;
import com.oinsexpress.ml.MLService;
import com.oinsexpress.repository.PositionRepository;
import com.oinsexpress.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SensorController — Reçoit les données IMU de l'ESP32
 *
 * Flux :
 *   ESP32 → POST /api/sensor/data
 *         → MLService → Flask IA /predict
 *         → sauvegarde Position (avec drivingState)
 *         → alerte uniquement si AGGRESSIVE
 *
 * PFA 2026 — ISTIC | Mrabet Islem & Merghni Ons
 */
@RestController
@RequestMapping("/api/sensor")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class SensorController {

    private final MLService          mlService;
    private final PositionRepository positionRepository;
    private final AlertService       alertService;

    /**
     * POST /api/sensor/data
     *
     * Body JSON (ESP32) :
     * {
     *   "livreurId": "LIV-001",
     *   "ax": 0.012, "ay": -0.003, "az": 1.001,
     *   "gx": 0.5,   "gy": -0.3,   "gz": 0.1,
     *   "latitude":  36.8065,   // optionnel
     *   "longitude": 10.1815    // optionnel
     * }
     */
    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> receiveData(@RequestBody SensorDTO dto) {

        String livreurId = dto.getLivreurId();
        double lat = dto.getLatitude()  != null ? dto.getLatitude()  : 0.0;
        double lon = dto.getLongitude() != null ? dto.getLongitude() : 0.0;

        // ── 1. Appel Flask IA ──
        DrivingState state = mlService.predict(
            livreurId,
            dto.getAx(), dto.getAy(), dto.getAz(),
            dto.getGx(), dto.getGy(), dto.getGz()
        );
        log.info("[Sensor] {} → {}", livreurId, state);

        // ── 2. Sauvegarder la position + état en base ──
        positionRepository.save(
            Position.builder()
                .livreurId(livreurId)
                .latitude(lat)
                .longitude(lon)
                .accX(dto.getAx()).accY(dto.getAy()).accZ(dto.getAz())
                .gyrX(dto.getGx()).gyrY(dto.getGy()).gyrZ(dto.getGz())
                .drivingState(state)
                .build()
        );

        // ── 3. Alerte uniquement si conduite AGRESSIVE (pas RISKY) ──
        if (state == DrivingState.AGGRESSIVE) {
            alertService.createDrivingStateAlert(livreurId, lat, lon, state.name());
        }

        // ── 4. Réponse ──
        String message = switch (state) {
            case AGGRESSIVE -> "Conduite agressive détectée — intervention requise";
            case RISKY      -> "Conduite risquée détectée — vigilance recommandée";
            default         -> "Conduite normale";
        };

        return ResponseEntity.ok(Map.of(
            "livreurId",    livreurId,
            "drivingState", state.name(),
            "status",       state == DrivingState.NORMAL ? "OK" : "ALERTE",
            "message",      message
        ));
    }

    /**
     * GET /api/sensor/health — Vérifie que Flask IA est disponible
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean iaUp = mlService.isHealthy();
        return ResponseEntity.ok(Map.of(
            "spring_boot", "UP",
            "flask_ia",    iaUp ? "UP" : "DOWN",
            "status",      iaUp ? "OK" : "DEGRADED"
        ));
    }
}
