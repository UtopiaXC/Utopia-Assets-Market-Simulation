package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.control.event.InterventionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * WebSocket status broadcaster
 * Sends simulation status updates to connected clients
 */
@Component
@EnableScheduling
public class SimulationStatusBroadcaster implements SimulationService.StatusListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimulationService simulationService;

    @PostConstruct
    public void init() {
        simulationService.addStatusListener(this);
    }

    @Override
    public void onStatusUpdate(SimulationSession.SessionStatus status) {
        // Real-time status push (rate limited by simulation speed)
        messagingTemplate.convertAndSend("/topic/simulation/status", status);
    }

    @Override
    public void onEventExecuted(InterventionEvent event) {
        messagingTemplate.convertAndSend("/topic/simulation/events", Map.of(
                "type", "EVENT_EXECUTED",
                "event", event.toMap()));
    }

    /**
     * Heartbeat - send status every 500ms even when simulation is not running
     */
    @Scheduled(fixedRate = 500)
    public void heartbeat() {
        SimulationSession.SessionStatus status = simulationService.getStatus();
        messagingTemplate.convertAndSend("/topic/simulation/heartbeat", status);
    }
}
