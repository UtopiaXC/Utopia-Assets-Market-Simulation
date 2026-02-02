package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing database connections to simulation result files
 */
@Service
public class DatabaseService {

    @Value("${simulation.output.directory:output}")
    private String outputDirectory;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
    }

    /**
     * List all available simulation database files
     */
    public List<SimulationFile> listSimulations() {
        List<SimulationFile> simulations = new ArrayList<>();
        File dir = new File(outputDirectory);

        if (!dir.exists() || !dir.isDirectory()) {
            return simulations;
        }

        File[] dbFiles = dir.listFiles((d, name) -> name.endsWith(".db"));
        if (dbFiles == null) {
            return simulations;
        }

        // Sort by modification time, newest first
        Arrays.sort(dbFiles, Comparator.comparingLong(File::lastModified).reversed());

        for (File file : dbFiles) {
            simulations.add(new SimulationFile(
                    file.getName(),
                    file.getAbsolutePath(),
                    file.lastModified(),
                    file.length()));
        }

        return simulations;
    }

    /**
     * Get a database connection for a specific simulation file
     */
    public Connection getConnection(String dbPath) throws SQLException {
        File file = new File(dbPath);
        if (!file.exists()) {
            // Try in output directory
            file = new File(outputDirectory, dbPath);
        }

        if (!file.exists()) {
            throw new SQLException("Database file not found: " + dbPath);
        }

        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    /**
     * Get connection by filename (looks in output directory)
     */
    public Connection getConnectionByName(String fileName) throws SQLException {
        return getConnection(new File(outputDirectory, fileName).getAbsolutePath());
    }

    /**
     * DTO for simulation file info
     */
    public static class SimulationFile {
        public String name;
        public String path;
        public long lastModified;
        public long size;

        public SimulationFile(String name, String path, long lastModified, long size) {
            this.name = name;
            this.path = path;
            this.lastModified = lastModified;
            this.size = size;
        }
    }
}
