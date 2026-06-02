package com.oinsexpress.controller;

import com.oinsexpress.dto.TrackingDtos.AlertDto;
import com.oinsexpress.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BOSS')")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertDto>> getAlerts(
            @RequestParam(defaultValue = "true") boolean unresolved) {
        return ResponseEntity.ok(alertService.getAlerts(unresolved));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveAlert(@PathVariable UUID id) {
        alertService.resolveAlert(id);
        return ResponseEntity.ok().build();
    }
}
