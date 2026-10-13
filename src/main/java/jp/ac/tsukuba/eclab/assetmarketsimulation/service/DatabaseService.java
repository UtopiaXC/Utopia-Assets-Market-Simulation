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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing database connections to simulation result files.
 * Now uses MyBatis SqlSessionFactory instead of raw JDBC connections.
 */
@Service
public class DatabaseService {

    @Value("${simulation.output.directory:output}")
    private String outputDirectory;

    private final Map<String, Integer> daysCache = new ConcurrentHashMap<>();

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
            int days = getMaxDaysForFile(file);
            simulations.add(new SimulationFile(
                    file.getName(),
                    file.getAbsolutePath(),
                    file.lastModified(),
                    file.length(),
                    days));
        }

        return simulations;
    }

    private int getMaxDaysForFile(File file) {
        String cacheKey = file.getName() + "_" + file.lastModified() + "_" + file.length();
        Integer cached = daysCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        int maxDays = 0;
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
             java.sql.Statement stmt = conn.createStatement()) {
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT MAX(day) FROM market_index_daily")) {
                if (rs.next()) {
                    maxDays = rs.getInt(1);
                }
            } catch (Exception ignored) {
                try (java.sql.ResultSet rs2 = stmt.executeQuery("SELECT MAX(day) FROM stock_daily")) {
                    if (rs2.next()) {
                        maxDays = rs2.getInt(1);
                    }
                } catch (Exception ignored2) {}
            }
        } catch (Exception ignored) {}

        daysCache.put(cacheKey, maxDays);
        return maxDays;
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
        public int days;

        public SimulationFile(String name, String path, long lastModified, long size) {
            this(name, path, lastModified, size, 0);
        }

        public SimulationFile(String name, String path, long lastModified, long size, int days) {
            this.name = name;
            this.path = path;
            this.lastModified = lastModified;
            this.size = size;
            this.days = days;
        }
    }
}
