package com.oinsexpress.controller;

import com.oinsexpress.dto.LivreurScoreDto;
import com.oinsexpress.entity.User;
import com.oinsexpress.service.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boss/scoring")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoringService;

    /**
     * GET /api/boss/scoring?mois=1
     * Retourne le classement des livreurs du boss connecté.
     * mois=1  → dernière semaine (démo)
     * mois=12 → 12 derniers mois (production)
     */
    @GetMapping
    @PreAuthorize("hasRole('BOSS')")
    public ResponseEntity<List<LivreurScoreDto>> getClassement(
        @AuthenticationPrincipal User boss,
        @RequestParam(defaultValue = "1") int mois
    ) {
        List<LivreurScoreDto> classement = scoringService.getClassement(
            boss.getId(), mois
        );
        return ResponseEntity.ok(classement);
    }
}
