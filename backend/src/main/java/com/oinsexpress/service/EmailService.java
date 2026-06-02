package com.oinsexpress.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${oinsexpress.mail.from}")
    private String fromEmail;

    @Value("${oinsexpress.brevo.api-key:}")
    private String brevoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Async
    public void sendVerificationCode(String to, String firstName, String code) {
        String subject = "OINSExpress — Code de vérification";
        String body = String.format("""
            Bonjour %s,

            Bienvenue sur OINSExpress !

            Votre code de vérification est : %s

            Ce code est valable pendant 15 minutes.

            Si vous n'avez pas créé de compte, ignorez ce message.

            ---
            L'équipe OINSExpress
            PFA 2026 — FST
            """, firstName, code);

        sendEmail(to, subject, body);
    }

    @Async
    public void sendResetPasswordCode(String to, String firstName, String code) {
        String subject = "OINSExpress — Réinitialisation du mot de passe";
        String body = String.format("""
            Bonjour %s,

            Vous avez demandé une réinitialisation de mot de passe.

            Votre code est : %s

            Ce code est valable pendant 15 minutes.

            Si vous n'avez pas fait cette demande, ignorez ce message.

            ---
            L'équipe OINSExpress
            """, firstName, code);

        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("📧 BREVO_API_KEY non configurée — email non envoyé à {}", to);
            log.warn("📧 CONTENU EMAIL (DEV FALLBACK) destinataire={} :\n{}", to, body);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> payload = Map.of(
                "sender",  Map.of("name", "OINSExpress", "email", fromEmail),
                "to",      List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Email envoyé via API Brevo à {}", to);
            } else {
                log.error("❌ Brevo API — réponse inattendue {} pour {}", response.getStatusCode(), to);
            }

        } catch (Exception e) {
            log.error("❌ Erreur Brevo API — impossible d'envoyer l'email à {} : {}", to, e.getMessage(), e);
            log.warn("📧 CONTENU EMAIL (DEV FALLBACK) destinataire={} :\n{}", to, body);
        }
    }
}
