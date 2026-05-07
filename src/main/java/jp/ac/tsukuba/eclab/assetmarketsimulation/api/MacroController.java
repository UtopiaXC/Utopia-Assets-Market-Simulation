package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.MacroAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for macro statistics
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/macro")
public class MacroController {

    @Autowired
    private MacroAnalysisService macroAnalysisService;

    @GetMapping
    public ResponseEntity<?> getMacroStats(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/population")
    public ResponseEntity<?> getPopulation(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("population", data.populationHistory));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/wealth")
    public ResponseEntity<?> getWealth(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("wealth", data.wealthHistory));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/agent-assets")
    public ResponseEntity<?> getAgentAssets(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("agentAssets", data.agentTypeAssets));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/agent-risk")
    public ResponseEntity<?> getAgentRisk(@PathVariable String dbFile) {
        try {
            MacroStatsDTO data = macroAnalysisService.getMacroStats(dbFile);
            return ResponseEntity.ok(Map.of("agentRisk", data.agentTypeRisk));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
