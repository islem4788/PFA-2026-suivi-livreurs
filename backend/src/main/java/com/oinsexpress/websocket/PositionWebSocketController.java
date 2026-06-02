package com.oinsexpress.websocket;

import com.oinsexpress.dto.TrackingDtos.PositionRequest;
import com.oinsexpress.dto.TrackingDtos.TrafficReportRequest;
import com.oinsexpress.service.AlertService;
import com.oinsexpress.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PositionWebSocketController {

    private final TrackingService trackingService;
    private final AlertService alertService;

    @MessageMapping("/position")
    public void handlePosition(PositionRequest req) {
        log.debug("Position reçue de {}", req.getLivreurId());
        trackingService.recordPosition(req);
    }

    @MessageMapping("/traffic")
    public void handleTraffic(TrafficReportRequest req) {
        log.info("Trafic signalé par {} : {}", req.getLivreurId(), req.getType());
        alertService.createTrafficAlert(req);
    }
}
