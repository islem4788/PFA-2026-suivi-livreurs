// ════════════════════════════════════════════════════════════════
// MODIFIER recordPosition() dans TrackingService.java
// Ajouter le champ webPushService et appel après IA
// ════════════════════════════════════════════════════════════════

// 1. AJOUTER dans les champs en haut de la classe :
private final WebPushService webPushService;

// 2. REMPLACER la méthode recordPosition() complète par :

@Transactional
public void recordPosition(PositionRequest req) {

    // Appeler classify() et récupérer le vrai résultat IA
    AnomalyDetectionService.AnomalyResult anomaly =
        anomalyDetection.classify(req);

    // Convertir String → DrivingState enum
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

    // Créer alerte + envoyer push notification fond d'écran
    if (anomaly.anomaly()) {
        alertService.createImuAnomalyAlert(req);

        // ✅ ENVOI PUSH NOTIFICATION FOND D'ECRAN
        String title = "AGGRESSIVE".equals(anomaly.drivingState())
            ? "🔴 Conduite dangereuse !"
            : "🟡 Conduite risquée";

        String body = String.format("Livreur %s — %s",
            req.getLivreurId(),
            "AGGRESSIVE".equals(anomaly.drivingState())
                ? "Intervention urgente !"
                : "Surveillance recommandée");

        webPushService.sendToAllBosses(title, body, "/boss/map");
    }

    // Diffusion WebSocket (gardé pour app ouverte)
    broadcastPosition(position, req);
}
