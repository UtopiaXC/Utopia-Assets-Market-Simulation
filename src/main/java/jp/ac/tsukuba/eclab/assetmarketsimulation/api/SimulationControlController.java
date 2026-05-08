package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationConfig;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationSession;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for simulation control and policy management
 */
@RestController
@RequestMapping("/api/control")
public class SimulationControlController {

    @Autowired
    private SimulationService simulationService;

    // =====================================================
    // Simulation Lifecycle
    // =====================================================

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
        List<String> sectors = Arrays.stream(Sector.values())
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
    // Policy Slot APIs
    // =====================================================

    /**
     * Get current policy slot values
     * GET /api/control/policy
     */
    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> getCurrentPolicy() {
        return ResponseEntity.ok(simulationService.getCurrentPolicy());
    }



    /**
     * Inject a scheduled policy change event.
     * The event will execute at the specified day during simulation.
     * POST /api/control/policy/inject
     *
     * Body: { "day": 100, "policyType": "CIRCUIT_BREAKER", "value": 0.05, "description": "..." }
     * Valid policyTypes: PRICE_LIMIT, CIRCUIT_BREAKER, LEVERAGE, SETTLEMENT
     */
    @PostMapping("/policy/inject")
    public ResponseEntity<?> injectPolicyEvent(@RequestBody Map<String, Object> params) {
        try {
            int day = ((Number) params.get("day")).intValue();
            String policyType = (String) params.get("policyType");
            double value = ((Number) params.get("value")).doubleValue();
            String description = (String) params.getOrDefault("description",
                    "Scheduled policy change: " + policyType + " -> " + value + " on day " + day);

            simulationService.injectPolicyEvent(day, policyType, value, description);
            return ResponseEntity.ok(Map.of(
                    "day", day,
                    "policyType", policyType,
                    "value", value,
                    "injected", true
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid policy type: " + e.getMessage()));
        }
    }
}
