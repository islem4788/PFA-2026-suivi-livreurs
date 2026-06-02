package com.oinsexpress.service;

import com.oinsexpress.dto.TrackingDtos.PositionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * OINSExpress — Service de détection d'anomalies IMU
 * =====================================================
 * Appelle le microservice Flask XGBoost pour classifier
 * le comportement de conduite en temps réel.
 *
 * Modèle XGBoost binaire (NORMAL / AGGRESSIVE)
 *   - Accuracy : 86.7%    - AUC : 0.951
 *   - Seuil WARNING  : P(AGGRESSIVE) >= 0.50  → RISKY
 *   - Seuil CRITICAL : P(AGGRESSIVE) >= 0.95  → AGGRESSIVE
 *
 * Features principales (top 3) :
 *   1. GyroZ_roll_mean (18.8%)
 *   2. GyroZ_roll_min  (10.1%)
 *   3. AccY_roll_max   ( 5.0%)
 */
@Service
@Slf4j
public class AnomalyDetectionService {

    @Value("${oinsexpress.ml.api-url:https://oinsexpress-ia.onrender.com}")
    private String mlApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Résultat de la classification IA.
     */
    public record AnomalyResult(
        boolean anomaly,
        String  drivingState,   // NORMAL | RISKY | AGGRESSIVE
        String  severity,       // NONE   | MEDIUM | HIGH
        String  message,
        double  proba
    ) {}

    /**
     * Classifie le comportement de conduite via le microservice Flask XGBoost.
     * Fallback automatique sur règles simples si le service est indisponible.
     *
     * @param req Position request contenant les données IMU (accX/Y/Z, gyrX/Y/Z)
     * @return Résultat de classification avec état, sévérité et message
     */
    public AnomalyResult classify(PositionRequest req) {

        // Pas de données IMU → pas d'analyse
        if (req.getAccX() == null || req.getAccY() == null) {
            return new AnomalyResult(false, "NORMAL", "NONE", "Pas de données IMU", 0.0);
        }

        try {
            // ── Construire le body JSON ──
            Map<String, Object> rawImu = new HashMap<>();
            rawImu.put("accX", req.getAccX() != null ? req.getAccX() :  0.0);
            rawImu.put("accY", req.getAccY() != null ? req.getAccY() :  0.0);
            rawImu.put("accZ", req.getAccZ() != null ? req.getAccZ() : -1.0);   // -1g (gravité), unité = g
            rawImu.put("gyrX", req.getGyrX() != null ? req.getGyrX() :  0.0);
            rawImu.put("gyrY", req.getGyrY() != null ? req.getGyrY() :  0.0);
            rawImu.put("gyrZ", req.getGyrZ() != null ? req.getGyrZ() :  0.0);

            Map<String, Object> body = new HashMap<>();
            body.put("livreurId", req.getLivreurId());
            body.put("raw_imu",   rawImu);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // ── Appel Flask XGBoost ──
            ResponseEntity<Map> response = restTemplate.postForEntity(
                mlApiUrl + "/predict",
                entity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<?, ?> result = response.getBody();
                String state = result.get("drivingState") != null
                        ? result.get("drivingState").toString() : "NORMAL";
                String sev = result.get("severity") != null
                        ? result.get("severity").toString() : "NONE";
                String msg = result.get("message") != null
                        ? result.get("message").toString() : "";
                double proba = result.get("proba") instanceof Number
                        ? ((Number) result.get("proba")).doubleValue() : 0.0;
                boolean alert = "AGGRESSIVE".equals(state) || "RISKY".equals(state);

                log.info("[IA XGBoost] {} → {} (sev={}, proba={})",
                        req.getLivreurId(), state, sev, proba);

                return new AnomalyResult(alert, state, sev, msg, proba);
            }

        } catch (Exception e) {
            log.warn("[IA] Microservice Flask indisponible : {} → fallback règles simples", e.getMessage());
            return classifyWithRules(req);
        }

        return new AnomalyResult(false, "NORMAL", "NONE", "", 0.0);
    }

    /**
     * Fallback : règles simples basées sur les features les plus importantes.
     *   - GyroZ_roll_mean : virage/dérapage
     *   - AccY_roll_max   : freinage/accélération latérale
     */
    private AnomalyResult classifyWithRules(PositionRequest req) {
        double ay = Math.abs(req.getAccY() != null ? req.getAccY() : 0);
        double gz = Math.abs(req.getGyrZ() != null ? req.getGyrZ() : 0);

        if (gz > 150 || ay > 2.5) {
            return new AnomalyResult(true, "AGGRESSIVE", "HIGH",
                "Conduite dangereuse (règles seuils — IA indisponible)", 0.99);
        } else if (gz > 80 || ay > 1.5) {
            return new AnomalyResult(true, "RISKY", "MEDIUM",
                "Conduite risquée (règles seuils — IA indisponible)", 0.65);
        }
        return new AnomalyResult(false, "NORMAL", "NONE",
            "Conduite normale", 0.10);
    }

    /** Rétrocompatibilité */
    public boolean isAnomaly(PositionRequest req) {
        return classify(req).anomaly();
    }
}
