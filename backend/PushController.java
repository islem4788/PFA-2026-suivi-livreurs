package com.oinsexpress.controller;

import com.oinsexpress.dto.PushSubscriptionDto;
import com.oinsexpress.entity.User;
import com.oinsexpress.service.WebPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    @Value("${oinsexpress.vapid.public-key:BDxX1HutyHIw-Z7ACV2gL9M8un-mRpmCIIg5OYJGxpf8ST3cPyO-yQJ_4cSwBr2sEE9NA9f53BpAKNaJwpeO3qc}")
    private String vapidPublicKey;

    private final WebPushService webPushService;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
        @AuthenticationPrincipal User user,
        @RequestBody PushSubscriptionDto dto
    ) {
        webPushService.saveSubscription(
            user.getId(),
            dto.getEndpoint(),
            dto.getKeys().getP256dh(),
            dto.getKeys().getAuth()
        );
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }
}
