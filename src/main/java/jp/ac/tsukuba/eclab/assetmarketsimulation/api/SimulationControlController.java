package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationConfig;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationSession;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.event.InterventionEvent;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for simulation control
 */
@RestController
@RequestMapping("/api/control")
public class SimulationControlController {

    @Autowired
    private SimulationService simulationService;

    /**
     * Get current simulation status
     * GET /api/control/status
     */
    @GetMapping("/status")
    public ResponseEntity<SimulationSession.SessionStatus> getStatus() {
        return ResponseEntity.ok(simulationService.getStatus());
    }

    /**
     * Start a new simulation
     * POST /api/control/start
     */
    @PostMapping("/start")
    public ResponseEntity<?> startSimulation(@RequestBody(required = false) SimulationConfig config) {
        try {
            if (config == null) {
                config = SimulationConfig.fromDefaults();
            }
            SimulationSession.SessionStatus status = simulationService.startSimulation(config);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Pause simulation
     * POST /api/control/pause
     */
    @PostMapping("/pause")
    public ResponseEntity<SimulationSession.SessionStatus> pause() {
        return ResponseEntity.ok(simulationService.pause());
    }

    /**
     * Resume simulation
     * POST /api/control/resume
     */
    @PostMapping("/resume")
    public ResponseEntity<SimulationSession.SessionStatus> resume() {
        return ResponseEntity.ok(simulationService.resume());
    }

    /**
     * Stop simulation
     * POST /api/control/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<SimulationSession.SessionStatus> stop() {
        return ResponseEntity.ok(simulationService.stop());
    }

    /**
     * Set simulation speed
     * POST /api/control/speed?multiplier=2.0
     */
    @PostMapping("/speed")
    public ResponseEntity<SimulationSession.SessionStatus> setSpeed(
            @RequestParam double multiplier) {
        return ResponseEntity.ok(simulationService.setSpeed(multiplier));
    }

    /**
     * Get default configuration
     * GET /api/control/config/default
     */
    @GetMapping("/config/default")
    public ResponseEntity<SimulationConfig> getDefaultConfig() {
        return ResponseEntity.ok(SimulationConfig.fromDefaults());
    }

    /**
     * Get available sectors
     * GET /api/control/sectors
     */
    @GetMapping("/sectors")
    public ResponseEntity<List<String>> getSectors() {
        List<String> sectors = java.util.Arrays.stream(Sector.values())
                .map(Sector::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sectors);
    }

    /**
     * Get current metrics
     * GET /api/control/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Double>> getMetrics() {
        return ResponseEntity.ok(simulationService.getCurrentMetrics());
    }

    // =====================================================
    // Event Injection APIs
    // =====================================================

    /**
     * Get pending events
     * GET /api/control/events/pending
     */
    @GetMapping("/events/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingEvents() {
        List<Map<String, Object>> events = simulationService.getPendingEvents()
                .stream()
                .map(InterventionEvent::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }

    /**
     * Get event history
     * GET /api/control/events/history
     */
    @GetMapping("/events/history")
    public ResponseEntity<List<Map<String, Object>>> getEventHistory() {
        List<Map<String, Object>> events = simulationService.getEventHistory()
                .stream()
                .map(InterventionEvent::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }

    /**
     * Cancel a pending event
     * DELETE /api/control/events/{eventId}
     */
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<?> cancelEvent(@PathVariable String eventId) {
        boolean cancelled = simulationService.cancelEvent(eventId);
        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

    /**
     * Inject rate cut event
     * POST /api/control/events/rate-cut
     */
    @PostMapping("/events/rate-cut")
    public ResponseEntity<?> injectRateCut(@RequestBody Map<String, Object> params) {
        int targetDay = ((Number) params.getOrDefault("targetDay", 0)).intValue();
        double liquidity = ((Number) params.getOrDefault("liquidityPerAgent", 1000000)).doubleValue();
        double riskBoost = ((Number) params.getOrDefault("riskBoost", 0.1)).doubleValue();

        if (targetDay <= 0) {
            SimulationSession session = simulationService.getCurrentSession();
            targetDay = session != null ? session.getCurrentDay() + 1 : 1;
        }

        String eventId = simulationService.injectRateCut(targetDay, liquidity, riskBoost);
        return ResponseEntity.ok(Map.of("eventId", eventId, "targetDay", targetDay));
    }

    /**
     * Inject rate hike event
     * POST /api/control/events/rate-hike
     */
    @PostMapping("/events/rate-hike")
    public ResponseEntity<?> injectRateHike(@RequestBody Map<String, Object> params) {
        int targetDay = ((Number) params.getOrDefault("targetDay", 0)).intValue();
        double liquidityRatio = ((Number) params.getOrDefault("liquidityRatio", 0.1)).doubleValue();
        double riskDrop = ((Number) params.getOrDefault("riskDrop", 0.1)).doubleValue();

        if (targetDay <= 0) {
            SimulationSession session = simulationService.getCurrentSession();
            targetDay = session != null ? session.getCurrentDay() + 1 : 1;
        }

        String eventId = simulationService.injectRateHike(targetDay, liquidityRatio, riskDrop);
        return ResponseEntity.ok(Map.of("eventId", eventId, "targetDay", targetDay));
    }

    /**
     * Inject sector sentiment event
     * POST /api/control/events/sector-sentiment
     */
    @PostMapping("/events/sector-sentiment")
    public ResponseEntity<?> injectSectorSentiment(@RequestBody Map<String, Object> params) {
        int targetDay = ((Number) params.getOrDefault("targetDay", 0)).intValue();
        String sector = (String) params.getOrDefault("sector", "TECH");
        double multiplier = ((Number) params.getOrDefault("multiplier", 1.5)).doubleValue();

        if (targetDay <= 0) {
            SimulationSession session = simulationService.getCurrentSession();
            targetDay = session != null ? session.getCurrentDay() + 1 : 1;
        }

        try {
            String eventId = simulationService.injectSectorSentiment(targetDay, sector, multiplier);
            return ResponseEntity.ok(Map.of("eventId", eventId, "targetDay", targetDay));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid sector: " + sector));
        }
    }

    /**
     * Inject sector fundamental event
     * POST /api/control/events/sector-fundamental
     */
    @PostMapping("/events/sector-fundamental")
    public ResponseEntity<?> injectSectorFundamental(@RequestBody Map<String, Object> params) {
        int targetDay = ((Number) params.getOrDefault("targetDay", 0)).intValue();
        String sector = (String) params.getOrDefault("sector", "CONSUMER");
        double epsChange = ((Number) params.getOrDefault("epsChange", -0.3)).doubleValue();

        if (targetDay <= 0) {
            SimulationSession session = simulationService.getCurrentSession();
            targetDay = session != null ? session.getCurrentDay() + 1 : 1;
        }

        try {
            String eventId = simulationService.injectSectorFundamental(targetDay, sector, epsChange);
            return ResponseEntity.ok(Map.of("eventId", eventId, "targetDay", targetDay));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid sector: " + sector));
        }
    }

    // =====================================================
    // FAVAR/EA/LLM Placeholder APIs
    // =====================================================

    /**
     * Apply FAVAR matrix intervention (placeholder)
     * POST /api/control/favar
     */
    @PostMapping("/favar")
    public ResponseEntity<?> applyFavarIntervention(@RequestBody Map<String, Object> params) {
        // TODO: Parse matrix from request body
        return ResponseEntity.ok(Map.of(
                "message", "FAVAR intervention API ready, implementation pending",
                "status", "NOT_IMPLEMENTED"));
    }

    /**
     * Get market context for LLM (placeholder)
     * GET /api/control/llm/context
     */
    @GetMapping("/llm/context")
    public ResponseEntity<Map<String, Object>> getLlmContext() {
        return ResponseEntity.ok(simulationService.getMarketContext());
    }

    /**
     * Get agent state for LLM (placeholder)
     * GET /api/control/llm/agent/{agentId}
     */
    @GetMapping("/llm/agent/{agentId}")
    public ResponseEntity<Map<String, Object>> getAgentState(@PathVariable int agentId) {
        return ResponseEntity.ok(simulationService.getAgentState(agentId));
    }
}
