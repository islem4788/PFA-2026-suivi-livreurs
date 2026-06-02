package com.oinsexpress.repository;

import com.oinsexpress.entity.User;
import com.oinsexpress.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByLivreurId(String livreurId);
    boolean existsByEmail(String email);
    boolean existsByLivreurId(String livreurId);
    List<User> findByRole(UserRole role);

    /** Tous les livreurs rattachés à un boss donné */
    List<User> findByBossId(UUID bossId);

    /** Tous les livreurs rattachés ET actifs */
    List<User> findByBossIdAndActiveTrue(UUID bossId);
}
