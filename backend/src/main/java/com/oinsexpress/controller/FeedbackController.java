package com.oinsexpress.controller;

import com.oinsexpress.dto.AuthDtos.MessageResponse;
import com.oinsexpress.dto.TrackingDtos.ClientFeedbackRequest;
import com.oinsexpress.entity.ClientFeedback;
import com.oinsexpress.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** Endpoint public — pas besoin de connexion */
    @PostMapping
    public ResponseEntity<MessageResponse> submit(@Valid @RequestBody ClientFeedbackRequest req) {
        feedbackService.submit(req);
        return ResponseEntity.ok(MessageResponse.builder()
            .message("Avis enregistré, merci !")
            .build());
    }

    /** Boss uniquement — voir tous les avis */
    @GetMapping
    @PreAuthorize("hasRole('BOSS')")
    public ResponseEntity<List<ClientFeedback>> getAll() {
        return ResponseEntity.ok(feedbackService.getAll());
    }

    /** Boss uniquement — voir les avis d'un colis */
    @GetMapping("/package/{packageId}")
    @PreAuthorize("hasRole('BOSS')")
    public ResponseEntity<List<ClientFeedback>> getByPackage(@PathVariable String packageId) {
        return ResponseEntity.ok(feedbackService.getByPackage(packageId));
    }
}
