package com.oinsexpress.service;

import com.oinsexpress.dto.LivreurScoreDto;
import com.oinsexpress.entity.DrivingState;
import com.oinsexpress.entity.User;
import com.oinsexpress.repository.AlertRepository;
import com.oinsexpress.repository.ClientFeedbackRepository;
import com.oinsexpress.repository.PositionRepository;
import com.oinsexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

    private final UserRepository           userRepository;
    private final PositionRepository       positionRepository;
    private final AlertRepository          alertRepository;
    private final ClientFeedbackRepository feedbackRepository;

    private static final double POIDS_CONDUITE = 0.40;
    private static final double POIDS_PRESENCE = 0.30;
    private static final double POIDS_ALERTES  = 0.30;

    public List<LivreurScoreDto> getClassement(UUID bossId, int mois) {
        List<User> livreurs = userRepository.findByBossId(bossId);
        if (livreurs.isEmpty()) return List.of();

        LocalDateTime depuis        = LocalDateTime.now().minusMonths(mois);
        LocalDateTime semainePassee = LocalDateTime.now().minusWeeks(1);

        // Moyenne globale des avis (partagée entre tous les livreurs)
        Double moyenneGlobale = feedbackRepository.avgRatingGlobal(depuis);
        double moyenneAvis = moyenneGlobale != null ? moyenneGlobale : 3.0;

        List<LivreurScoreDto> scores = new ArrayList<>();
        for (User livreur : livreurs) {
            if (!"LIVREUR".equals(livreur.getRole().name())) continue;
            scores.add(calculerScore(livreur, depuis, semainePassee, moyenneAvis));
        }

        scores.sort(Comparator.comparingDouble(LivreurScoreDto::getScoreFinal).reversed());
        AtomicInteger rang = new AtomicInteger(1);
        scores.forEach(s -> s.setRang(rang.getAndIncrement()));
        return scores;
    }

    private LivreurScoreDto calculerScore(User livreur,
                                          LocalDateTime depuis,
                                          LocalDateTime semainePassee,
                                          double moyenneAvis) {
        String lid = livreur.getLivreurId();

        // ── 1. Score conduite ── ✅ DrivingState enum
        long totalPos  = countPositions(lid, depuis);
        long normalPos = countPositionsByState(lid, depuis, DrivingState.NORMAL);
        long riskyPos  = countPositionsByState(lid, depuis, DrivingState.RISKY);
        long aggrPos   = countPositionsByState(lid, depuis, DrivingState.AGGRESSIVE);

        double scoreConduite = 100.0;
        if (totalPos > 0) {
            double pts = normalPos * 1.0 + riskyPos * 0.5;
            scoreConduite = (pts / totalPos) * 100.0;
        }

        // ── 2. Score présence ──
        long joursConnecte = getJoursConnecte(lid, depuis);
        long joursTotal = java.time.temporal.ChronoUnit.DAYS.between(
            livreur.getCreatedAt().toLocalDate(),
            LocalDateTime.now().toLocalDate()
        );
        long joursTravail = (joursTotal / 7) * 5 + Math.min(joursTotal % 7, 5);
        double scorePresence = joursTravail > 0
            ? Math.min((double) joursConnecte / joursTravail * 100.0, 100.0)
            : 50.0;

        // ── 3. Score alertes (inverse) ──
        long   nombreAlertes = getNombreAlertes(lid, depuis);
        double scoreAlertes  = Math.max(0.0, 100.0 - (nombreAlertes * 10.0));

        // ── Score final ──
        double scoreFinal = scoreConduite * POIDS_CONDUITE
                          + scorePresence * POIDS_PRESENCE
                          + scoreAlertes  * POIDS_ALERTES;
        scoreFinal = Math.round(scoreFinal * 10.0) / 10.0;

        // ── Évolution ──
        double scoreSemPassee = calculerScoreSimple(lid, semainePassee,
            LocalDateTime.now().minusWeeks(2));
        double evolution = Math.round((scoreFinal - scoreSemPassee) * 10.0) / 10.0;
        String tendance  = evolution > 2.0 ? "UP"
                         : evolution < -2.0 ? "DOWN"
                         : "STABLE";

        // ── Badge ──
        String badge, badgeColor;
        if      (scoreFinal >= 80) { badge = "EXCELLENT";   badgeColor = "#10B981"; }
        else if (scoreFinal >= 65) { badge = "BON";         badgeColor = "#3B82F6"; }
        else if (scoreFinal >= 50) { badge = "MOYEN";       badgeColor = "#F59E0B"; }
        else                       { badge = "A AMELIORER"; badgeColor = "#EF4444"; }

        // Score avis = moyenne globale convertie en 0-100
        double scoreAvis = ((moyenneAvis - 1.0) / 4.0) * 100.0;
        long nombreAvis  = feedbackRepository.countByCreatedAtAfter(depuis);

        return LivreurScoreDto.builder()
            .livreurId(lid)
            .firstName(livreur.getFirstName())
            .lastName(livreur.getLastName())
            .scoreFinal(scoreFinal)
            .scoreConduite(Math.round(scoreConduite * 10.0) / 10.0)
            .scoreAvis(Math.round(scoreAvis * 10.0) / 10.0)
            .scorePresence(Math.round(scorePresence * 10.0) / 10.0)
            .scoreAlertes(Math.round(scoreAlertes * 10.0) / 10.0)
            .evolutionScore(evolution)
            .tendance(tendance)
            .totalPositions(totalPos)
            .positionsNormal(normalPos)
            .positionsRisky(riskyPos)
            .positionsAggressive(aggrPos)
            .moyenneAvis(Math.round(moyenneAvis * 10.0) / 10.0)
            .nombreAvis(nombreAvis)
            .nombreAlertes(nombreAlertes)
            .joursConnecte(joursConnecte)
            .badge(badge)
            .badgeColor(badgeColor)
            .build();
    }

    private double calculerScoreSimple(String lid, LocalDateTime debut, LocalDateTime fin) {
        try {
            long total  = positionRepository.countByLivreurIdAndRecordedAtBetween(lid, debut, fin);
            long normal = positionRepository.countByLivreurIdAndDrivingStateAndRecordedAtBetween(
                lid, DrivingState.NORMAL, debut, fin);
            return total == 0 ? 50.0 : (double) normal / total * 100.0;
        } catch (Exception e) { return 50.0; }
    }

    private long countPositions(String lid, LocalDateTime depuis) {
        try { return positionRepository.countByLivreurIdAndRecordedAtAfter(lid, depuis); }
        catch (Exception e) { return 0L; }
    }

    private long countPositionsByState(String lid, LocalDateTime depuis, DrivingState state) {
        try { return positionRepository
                .countByLivreurIdAndDrivingStateAndRecordedAtAfter(lid, state, depuis); }
        catch (Exception e) { return 0L; }
    }

    private long getJoursConnecte(String lid, LocalDateTime depuis) {
        try { return positionRepository.countDistinctDaysByLivreurIdAfter(lid, depuis); }
        catch (Exception e) { return 0L; }
    }

    private long getNombreAlertes(String lid, LocalDateTime depuis) {
        try { return alertRepository.countByLivreurIdAndCreatedAtAfter(lid, depuis); }
        catch (Exception e) { return 0L; }
    }
}
