package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StylizedFactsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.StylizedFactsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for Stylized Facts Validation
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/validation")
public class ValidationController {

    @Autowired
    private StylizedFactsService stylizedFactsService;

    @GetMapping
    public ResponseEntity<?> getStylizedFacts(
            @PathVariable String dbFile,
            @RequestParam(required = false) Integer stockId) {
        try {
            StylizedFactsDTO data = stylizedFactsService.getStylizedFacts(dbFile, stockId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
