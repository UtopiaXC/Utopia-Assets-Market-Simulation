package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.data.DynamicSqlSessionManager;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing database connections to simulation result files.
 * Now uses MyBatis SqlSessionFactory instead of raw JDBC connections.
 */
@Service
public class DatabaseService {

    @Value("${simulation.output.directory:output}")
    private String outputDirectory;

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
     * Get a MyBatis SqlSession for a specific simulation file.
     * Caller is responsible for closing the session.
     */
    public SqlSession openSession(String dbFileName) {
        String dbPath = resolveDbPath(dbFileName);
        return DynamicSqlSessionManager.openSession(dbPath);
    }

    /**
     * Get the SqlSessionFactory for a specific simulation file.
     */
    public SqlSessionFactory getSessionFactory(String dbFileName) {
        String dbPath = resolveDbPath(dbFileName);
        return DynamicSqlSessionManager.getOrCreate(dbPath);
    }

    private String resolveDbPath(String dbFileName) {
        File file = new File(dbFileName);
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        file = new File(outputDirectory, dbFileName);
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        throw new RuntimeException("Database file not found: " + dbFileName);
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
