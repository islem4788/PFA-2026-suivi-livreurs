package com.oinsexpress.service;

import com.oinsexpress.dto.TrackingDtos.ClientFeedbackRequest;
import com.oinsexpress.entity.ClientFeedback;
import com.oinsexpress.repository.ClientFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final ClientFeedbackRepository feedbackRepository;
    private final AlertService alertService;

    @Transactional
    public ClientFeedback submit(ClientFeedbackRequest req) {
        ClientFeedback feedback = ClientFeedback.builder()
            .packageId(req.getPackageId())
            .clientName(req.getClientName())
            .clientAddress(req.getClientAddress())
            .comment(req.getComment())
            .rating(req.getRating())
            .build();

        feedbackRepository.save(feedback);

        // Si la note est mauvaise (≤ 2), créer une alerte pour le boss
        if (req.getRating() != null && req.getRating() <= 2) {
            alertService.createClientComplaintAlert(
                req.getPackageId(),
                req.getClientName(),
                req.getComment()
            );
        }

        log.info("Feedback reçu pour colis {} (rating={})", req.getPackageId(), req.getRating());
        return feedback;
    }

    public List<ClientFeedback> getAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ClientFeedback> getByPackage(String packageId) {
        return feedbackRepository.findByPackageId(packageId);
    }
}
