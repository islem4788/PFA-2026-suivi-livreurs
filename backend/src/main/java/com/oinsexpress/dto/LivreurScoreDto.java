package com.oinsexpress.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LivreurScoreDto {

    private String  livreurId;
    private String  firstName;
    private String  lastName;
    private int     rang;           // 1 = meilleur

    // Score global 0-100
    private double  scoreFinal;

    // Sous-scores 0-100
    private double  scoreConduite;   // basé sur drivingState
    private double  scoreAvis;       // basé sur avis clients
    private double  scorePresence;   // basé sur connexions
    private double  scoreAlertes;    // basé sur nb alertes (inverse)

    // Évolution par rapport à la semaine précédente
    private double  evolutionScore;  // + = amélioration, - = dégradation
    private String  tendance;        // "UP", "DOWN", "STABLE"

    // Stats brutes
    private long    totalPositions;
    private long    positionsNormal;
    private long    positionsRisky;
    private long    positionsAggressive;
    private double  moyenneAvis;
    private long    nombreAvis;
    private long    nombreAlertes;
    private long    joursConnecte;

    // Badge selon le score
    private String  badge;    // "EXCELLENT", "BON", "MOYEN", "A_AMELIORER"
    private String  badgeColor;
}
