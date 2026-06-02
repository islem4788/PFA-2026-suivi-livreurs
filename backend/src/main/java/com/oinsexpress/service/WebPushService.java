package com.oinsexpress.service;

import com.oinsexpress.entity.PushSubscriptionEntity;
import com.oinsexpress.entity.User;
import com.oinsexpress.repository.PushSubscriptionRepository;
import com.oinsexpress.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushService {

    @Value("${oinsexpress.vapid.public-key:BDxX1HutyHIw-Z7ACV2gL9M8un-mRpmCIIg5OYJGxpf8ST3cPyO-yQJ_4cSwBr2sEE9NA9f53BpAKNaJwpeO3qc}")
    private String vapidPublicKey;

    @Value("${oinsexpress.vapid.private-key:wjhJJ1xOrc2cgcCE_EkJL6IvDkdxstxTkeNzlY-YZdM}")
    private String vapidPrivateKey;

    @Value("${oinsexpress.vapid.subject:mailto:merghnichrifa@gmail.com}")
    private String vapidSubject;

    private final PushSubscriptionRepository subscriptionRepo;
    private final UserRepository userRepository;

    private PushService pushService;

    @PostConstruct
    public void init() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            log.info("[WebPush] Service initialisé avec clés VAPID");
        } catch (Exception e) {
            log.error("[WebPush] Erreur init: {}", e.getMessage());
        }
    }

    @Transactional
    public void saveSubscription(UUID userId, String endpoint, String p256dh, String auth) {
        // ✅ Supprimer TOUS les anciens abonnements → garder 1 seul par boss
        subscriptionRepo.deleteByUserId(userId);
        subscriptionRepo.flush();

        PushSubscriptionEntity sub = PushSubscriptionEntity.builder()
            .userId(userId)
            .endpoint(endpoint)
            .p256dh(p256dh)
            .auth(auth)
            .build();
        subscriptionRepo.save(sub);
        log.info("[WebPush] Abonnement enregistré pour user {} (anciens supprimés)", userId);
    }

    public void sendToBoss(UUID bossId, String title, String body, String url) {
        List<PushSubscriptionEntity> subs = subscriptionRepo.findByUserId(bossId);
        log.info("[WebPush] Envoi à boss {} — {} abonnement(s)", bossId, subs.size());
        for (PushSubscriptionEntity sub : subs) {
            sendNotification(sub, title, body, url);
        }
    }

    public void sendToAllBosses(String title, String body, String url) {
        List<User> bosses = userRepository.findAll().stream()
            .filter(u -> "BOSS".equals(u.getRole().name()))
            .toList();
        for (User boss : bosses) {
            sendToBoss(boss.getId(), title, body, url);
        }
    }

    private void sendNotification(PushSubscriptionEntity sub, String title, String body, String url) {
        try {
            String payload = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}",
                title.replace("\"", "\\\""),
                body.replace("\"", "\\\""),
                url
            );

            Subscription subscription = new Subscription(
                sub.getEndpoint(),
                new Subscription.Keys(sub.getP256dh(), sub.getAuth())
            );

            Notification notification = new Notification(subscription, payload);
            pushService.send(notification);
            log.info("[WebPush] ✅ Notification envoyée à {}", sub.getEndpoint().substring(0, 50) + "...");
        } catch (Exception e) {
            log.error("[WebPush] Erreur envoi: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("410")) {
                subscriptionRepo.deleteByEndpoint(sub.getEndpoint());
                log.info("[WebPush] Abonnement expiré supprimé");
            }
        }
    }
}
