package com.oinsexpress.service;

import com.oinsexpress.dto.UserDto;
import com.oinsexpress.entity.User;
import com.oinsexpress.entity.UserRole;
import com.oinsexpress.exception.BadRequestException;
import com.oinsexpress.exception.ConflictException;
import com.oinsexpress.exception.NotFoundException;
import com.oinsexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestion du lien Boss ↔ Livreurs.
 * Le Boss ajoute un livreur à son équipe via le code LIV-XXX.
 * Le Boss peut retirer un livreur (le livreur perd alors l'accès à l'app).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BossLivreurService {

    private final UserRepository userRepository;

    /**
     * Ajoute un livreur à l'équipe du boss en utilisant son code LIV-XXX.
     */
    @Transactional
    public UserDto addLivreur(UUID bossId, String livreurCode) {
        // Vérifier que le code livreur existe
        User livreur = userRepository.findByLivreurId(livreurCode.trim().toUpperCase())
            .orElseThrow(() -> new NotFoundException(
                "Aucun livreur trouvé avec le code " + livreurCode
            ));

        // Vérifier que c'est bien un livreur (pas un boss)
        if (livreur.getRole() != UserRole.LIVREUR) {
            throw new BadRequestException("Ce code n'appartient pas à un livreur");
        }

        // Vérifier que son email est vérifié
        if (!livreur.isEmailVerified()) {
            throw new BadRequestException("Ce livreur n'a pas encore vérifié son email");
        }

        // Vérifier qu'il n'est pas déjà rattaché à un autre boss
        if (livreur.getBossId() != null && !livreur.getBossId().equals(bossId)) {
            throw new ConflictException(
                "Ce livreur est déjà rattaché à une autre équipe"
            );
        }

        // Vérifier qu'il n'est pas déjà dans l'équipe du boss
        if (bossId.equals(livreur.getBossId())) {
            throw new ConflictException("Ce livreur fait déjà partie de votre équipe");
        }

        // Rattacher le livreur et réactiver son compte
        livreur.setBossId(bossId);
        livreur.setActive(true);
        userRepository.save(livreur);

        log.info("Boss {} a ajouté le livreur {} ({})", bossId, livreurCode, livreur.getId());

        return UserDto.fromEntity(livreur);
    }

    /**
     * Retire un livreur de l'équipe du boss.
     * Le livreur est désactivé : il perd l'accès à l'application.
     */
    @Transactional
    public void removeLivreur(UUID bossId, UUID livreurId) {
        User livreur = userRepository.findById(livreurId)
            .orElseThrow(() -> new NotFoundException("Livreur introuvable"));

        // Vérifier que ce livreur appartient bien au boss
        if (livreur.getBossId() == null || !livreur.getBossId().equals(bossId)) {
            throw new BadRequestException(
                "Ce livreur ne fait pas partie de votre équipe"
            );
        }

        // Détacher et désactiver
        livreur.setBossId(null);
        livreur.setActive(false);
        userRepository.save(livreur);

        log.info("Boss {} a retiré le livreur {} (compte désactivé)", bossId, livreurId);
    }

    /**
     * Retourne tous les livreurs de l'équipe du boss.
     */
    public List<UserDto> getMyLivreurs(UUID bossId) {
        return userRepository.findByBossId(bossId).stream()
            .map(UserDto::fromEntity)
            .toList();
    }
}
