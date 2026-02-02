package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.MacroAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

/**
 * REST API for macro statistics
 * Corresponds to Python tabs/macro.py
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/macro")
public class MacroController {

    @Autowired
    private MacroAnalysisService macroAnalysisService;

    /**
     * Get all macro statistics
     * GET /api/simulations/{dbFile}/macro
     */
    @GetMapping
    public ResponseEntity<?> getMacroStats(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(data);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get population history only
     * GET /api/simulations/{dbFile}/macro/population
     */
    @GetMapping("/population")
    public ResponseEntity<?> getPopulation(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("population", data.populationHistory));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get wealth structure history only
     * GET /api/simulations/{dbFile}/macro/wealth
     */
    @GetMapping("/wealth")
    public ResponseEntity<?> getWealth(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("wealth", data.wealthHistory));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get agent type assets history
     * GET /api/simulations/{dbFile}/macro/agent-assets
     */
    @GetMapping("/agent-assets")
    public ResponseEntity<?> getAgentAssets(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("agentAssets", data.agentTypeAssets));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get agent type risk tolerance history
     * GET /api/simulations/{dbFile}/macro/agent-risk
     */
    @GetMapping("/agent-risk")
    public ResponseEntity<?> getAgentRisk(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("agentRisk", data.agentTypeRisk));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
