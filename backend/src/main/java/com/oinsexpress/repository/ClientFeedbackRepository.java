package com.oinsexpress.repository;

import com.oinsexpress.entity.ClientFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClientFeedbackRepository extends JpaRepository<ClientFeedback, UUID> {

    List<ClientFeedback> findAllByOrderByCreatedAtDesc();
    List<ClientFeedback> findByPackageId(String packageId);

    // ═══════════════════════════════════════════════════════════
    // SCORING IA — pas de livreurId dans ClientFeedback
    // On utilise la moyenne globale de tous les avis
    // ═══════════════════════════════════════════════════════════

    /** Moyenne globale des notes depuis une date */
    @Query("SELECT AVG(f.rating) FROM ClientFeedback f " +
           "WHERE f.createdAt >= :since")
    Double avgRatingGlobal(@Param("since") LocalDateTime since);

    /** Compter tous les avis depuis une date */
    long countByCreatedAtAfter(LocalDateTime since);
}
