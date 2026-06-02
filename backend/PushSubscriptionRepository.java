package com.oinsexpress.repository;

import com.oinsexpress.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, UUID> {
    List<PushSubscriptionEntity> findByUserId(UUID userId);
    void deleteByEndpoint(String endpoint);
}
