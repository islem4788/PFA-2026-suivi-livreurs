package com.oinsexpress.repository;

import com.oinsexpress.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByResolvedFalseOrderByCreatedAtDesc();
    List<Alert> findAllByOrderByCreatedAtDesc();
    List<Alert> findByLivreurIdOrderByCreatedAtDesc(String livreurId);
    long countByResolvedFalse();

    // ═══════════════════════════════════════════════════════════
    // SCORING IA — Méthodes ajoutées
    // ═══════════════════════════════════════════════════════════

    /** Compter alertes d'un livreur depuis une date */
    long countByLivreurIdAndCreatedAtAfter(String livreurId, LocalDateTime since);
}
