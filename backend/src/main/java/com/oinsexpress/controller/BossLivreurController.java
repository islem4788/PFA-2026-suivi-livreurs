package com.oinsexpress.controller;

import com.oinsexpress.dto.AuthDtos.MessageResponse;
import com.oinsexpress.dto.UserDto;
import com.oinsexpress.entity.User;
import com.oinsexpress.service.BossLivreurService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints réservés au boss pour gérer son équipe de livreurs.
 */
@RestController
@RequestMapping("/api/boss/livreurs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BOSS')")
public class BossLivreurController {

    private final BossLivreurService service;

    /**
     * Liste tous les livreurs de l'équipe du boss connecté.
     */
    @GetMapping
    public ResponseEntity<List<UserDto>> getMyLivreurs(
            @AuthenticationPrincipal User boss) {
        return ResponseEntity.ok(service.getMyLivreurs(boss.getId()));
    }

    /**
     * Ajoute un livreur à l'équipe via son code LIV-XXX.
     */
    @PostMapping("/add")
    public ResponseEntity<UserDto> addLivreur(
            @AuthenticationPrincipal User boss,
            @Valid @RequestBody AddLivreurRequest request) {
        UserDto livreur = service.addLivreur(boss.getId(), request.code);
        return ResponseEntity.ok(livreur);
    }

    /**
     * Retire un livreur de l'équipe (désactive son compte).
     */
    @DeleteMapping("/{livreurId}")
    public ResponseEntity<MessageResponse> removeLivreur(
            @AuthenticationPrincipal User boss,
            @PathVariable UUID livreurId) {
        service.removeLivreur(boss.getId(), livreurId);
        return ResponseEntity.ok(MessageResponse.builder()
            .message("Livreur retiré de votre équipe")
            .build());
    }

    /** DTO pour la requête d'ajout */
    public static class AddLivreurRequest {
        @NotBlank(message = "Le code est requis")
        @Pattern(
            regexp = "^LIV-\\d{3,}$",
            message = "Format invalide (ex: LIV-001)"
        )
        public String code;
    }
}
