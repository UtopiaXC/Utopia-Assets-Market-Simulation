package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.service.DatabaseService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.DatabaseService.SimulationFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * REST API for simulation management
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    @Autowired
    private DatabaseService databaseService;

    /**
     * List all available simulation result files
     * GET /api/simulations
     */
    @GetMapping
    public ResponseEntity<List<SimulationFile>> listSimulations() {
        List<SimulationFile> simulations = databaseService.listSimulations();
        return ResponseEntity.ok(simulations);
    }

    /**
     * Get simulation metadata
     * GET /api/simulations/{fileName}/info
     */
    @GetMapping("/{fileName}/info")
    public ResponseEntity<Map<String, Object>> getSimulationInfo(@PathVariable String fileName) {
        try {
            String decodedName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            SimulationFile file = databaseService.listSimulations().stream()
                    .filter(f -> f.name.equals(decodedName))
                    .findFirst()
                    .orElse(null);

            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(Map.of(
                    "name", file.name,
                    "path", file.path,
                    "lastModified", file.lastModified,
                    "size", file.size));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Rename a simulation result file
     * POST /api/simulations/{fileName}/rename
     */
    @PostMapping("/{fileName}/rename")
    public ResponseEntity<Map<String, Object>> renameSimulation(
            @PathVariable String fileName,
            @RequestBody Map<String, String> body) {
        try {
            String decodedName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String newName = body.get("newName");

            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "New name is required"));
            }

            // Ensure .db extension
            if (!newName.endsWith(".db")) {
                newName = newName + ".db";
            }

            SimulationFile file = databaseService.listSimulations().stream()
                    .filter(f -> f.name.equals(decodedName))
                    .findFirst()
                    .orElse(null);

            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            File oldFile = new File(file.path);
            File newFile = new File(oldFile.getParent(), newName);

            if (newFile.exists()) {
                return ResponseEntity.badRequest().body(Map.of("error", "A file with that name already exists"));
            }

            boolean success = oldFile.renameTo(newFile);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "newName", newName));
            } else {
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to rename file"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a simulation result file
     * DELETE /api/simulations/{fileName}
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteSimulation(@PathVariable String fileName) {
        try {
            String decodedName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);

            SimulationFile file = databaseService.listSimulations().stream()
                    .filter(f -> f.name.equals(decodedName))
                    .findFirst()
                    .orElse(null);

            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            File dbFile = new File(file.path);
            boolean success = dbFile.delete();

            if (success) {
                return ResponseEntity.ok(Map.of("success", true));
            } else {
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete file"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
