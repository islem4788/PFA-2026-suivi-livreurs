package com.oinsexpress.service;

import com.oinsexpress.entity.Position;
import com.oinsexpress.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Vérifie périodiquement si un livreur est immobile depuis trop longtemps.
 * Si oui, crée une alerte STATIONARY_TIMEOUT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StationaryTimeoutScheduler {

    private final PositionRepository positionRepository;
    private final AlertService alertService;
    private final Set<String> alreadyAlerted = new HashSet<>();

    @Value("${oinsexpress.ml.stationary-timeout-minutes}")
    private int timeoutMinutes;

    private static final double STATIONARY_RADIUS_METERS = 50.0;

    @Scheduled(fixedRate = 60000) // toutes les minutes
    public void checkStationaryLivreurs() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        var activeLivreurs = positionRepository.findActiveLivreursSince(since);

        for (String livreurId : activeLivreurs) {
            checkLivreur(livreurId);
        }
    }

    private void checkLivreur(String livreurId) {
        Optional<Position> latestOpt = positionRepository
            .findFirstByLivreurIdOrderByRecordedAtDesc(livreurId);
        if (latestOpt.isEmpty()) return;

        Position latest = latestOpt.get();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);

        // Le livreur est-il immobile depuis le seuil ?
        var history = positionRepository.findHistoryByLivreur(livreurId, threshold);

        if (history.size() < 3) return; // pas assez de données

        // Vérifier que toutes les positions sont dans un rayon
        Position reference = history.get(0);
        boolean allWithinRadius = history.stream().allMatch(p ->
            distance(reference, p) < STATIONARY_RADIUS_METERS
        );

        if (allWithinRadius && !alreadyAlerted.contains(livreurId)) {
            log.warn("Livreur {} immobile depuis {} minutes", livreurId, timeoutMinutes);
            alertService.createStationaryAlert(
                livreurId,
                latest.getLatitude(),
                latest.getLongitude()
            );
            alreadyAlerted.add(livreurId);
        } else if (!allWithinRadius) {
            // Le livreur a bougé — on peut le re-alerter plus tard
            alreadyAlerted.remove(livreurId);
        }
    }

    /** Calcul Haversine simplifié pour distances < 1km */
    private double distance(Position a, Position b) {
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());

        double h = Math.sin(dLat/2) * Math.sin(dLat/2)
                 + Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1-h));
        return 6371000 * c;
    }
}
