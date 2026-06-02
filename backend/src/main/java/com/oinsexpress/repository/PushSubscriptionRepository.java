package com.oinsexpress.repository;

import com.oinsexpress.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, UUID> {
    List<PushSubscriptionEntity> findByUserId(UUID userId);
    
    @Transactional
    void deleteByEndpoint(String endpoint);
    
    @Transactional
    void deleteByUserId(UUID userId);  //  Supprime tous les anciens abonnements
}