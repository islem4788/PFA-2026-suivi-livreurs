package com.oinsexpress.controller;

import com.oinsexpress.dto.TrackingDtos.*;
import com.oinsexpress.entity.User;
import com.oinsexpress.exception.NotFoundException;
import com.oinsexpress.service.AlertService;
import com.oinsexpress.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final AlertService alertService;

    /**
     * Retourne uniquement les livreurs rattachés au boss connecté.
     */
    @GetMapping("/livreurs/positions")
    @PreAuthorize("hasRole('BOSS')")
    public ResponseEntity<List<LivreurPositionDto>> getAllLivreurs(
            @AuthenticationPrincipal User boss) {
        return ResponseEntity.ok(trackingService.getLatestPositionsForBoss(boss.getId()));
    }

    @GetMapping("/livreurs/{id}")
    @PreAuthorize("hasRole('BOSS')")
    public ResponseEntity<LivreurPositionDto> getLivreur(@PathVariable String id) {
        return ResponseEntity.ok(
            trackingService.getLivreurDetails(id)
                .orElseThrow(() -> new NotFoundException("Livreur non trouvé"))
        );
    }

    @PostMapping("/positions")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<Void> postPosition(@Valid @RequestBody PositionRequest req) {
        trackingService.recordPosition(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/traffic")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<Void> reportTraffic(@Valid @RequestBody TrafficReportRequest req) {
        alertService.createTrafficAlert(req);
        return ResponseEntity.ok().build();
    }
}
