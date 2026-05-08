package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.ScenarioRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    @GetMapping
    public ResponseEntity<List<ScenarioRegistry.ScenarioInfo>> listScenarios() {
        return ResponseEntity.ok(ScenarioRegistry.listScenarios());
    }
}
