package com.oinsexpress.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oinsexpress.entity.DrivingState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MLService — Appel vers Flask IA (XGBoost)
 * PFA 2026 — ISTIC | Mrabet Islem & Merghni Ons
 */
@Service
@Slf4j
public class MLService {

    @Value("${oinsexpress.ml.api-url:https://oinsexpress-ia.onrender.com}")
    private String mlApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Envoie les données IMU au Flask IA → retourne DrivingState (NORMAL / RISKY / AGGRESSIVE)
     *
     * @param livreurId identifiant du livreur (fenêtre glissante côté Flask)
     */
    public DrivingState predict(String livreurId,
                                double accX, double accY, double accZ,
                                double gyrX, double gyrY, double gyrZ) {
        try {
            // ── Corps JSON attendu par Flask ──
            // { "livreurId": "...", "raw_imu": { "accX":..., "gyrZ":... } }
            Map<String, Object> imu = new HashMap<>();
            imu.put("accX", accX);
            imu.put("accY", accY);
            imu.put("accZ", accZ);
            imu.put("gyrX", gyrX);
            imu.put("gyrY", gyrY);
            imu.put("gyrZ", gyrZ);

            Map<String, Object> body = new HashMap<>();
            body.put("livreurId", livreurId);
            body.put("raw_imu",   imu);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                mlApiUrl + "/predict", request, String.class
            );

            if (response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                if (json.has("drivingState")) {
                    return DrivingState.valueOf(json.get("drivingState").asText());
                }
            }

            return DrivingState.NORMAL;

        } catch (Exception e) {
            log.error("[MLService] Erreur appel IA : {}", e.getMessage());
            return DrivingState.NORMAL;   // fail-safe : pas d'alerte intempestive
        }
    }

    /**
     * Vérifie que Flask IA est disponible
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                mlApiUrl + "/health", String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[MLService] Health check failed : {}", e.getMessage());
            return false;
        }
    }
}