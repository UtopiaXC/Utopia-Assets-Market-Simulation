package jp.ac.tsukuba.eclab.assetmarketsimulation.data;

import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic SqlSessionFactory instances for per-simulation SQLite databases.
 * Supports both simulation writing and analysis reading.
 * Designed to be database-agnostic: swap driver + URL for MySQL migration.
 */
public class DynamicSqlSessionManager {

    private static final Map<String, SqlSessionFactory> factoryCache = new ConcurrentHashMap<>();

    /**
     * Create or retrieve a SqlSessionFactory for the given SQLite database path.
     */
    public static SqlSessionFactory getOrCreate(String dbPath) {
        return factoryCache.computeIfAbsent(dbPath, DynamicSqlSessionManager::buildFactory);
    }

    /**
     * Create a new SqlSessionFactory (for simulation writing).
     * Does NOT cache — caller manages lifecycle.
     */
    public static SqlSessionFactory createNew(String dbPath) {
        return buildFactory(dbPath);
    }

    /**
     * Remove a cached factory (call when done with a db file).
     */
    public static void remove(String dbPath) {
        factoryCache.remove(dbPath);
    }

    /**
     * Open a new SqlSession. Caller is responsible for closing it.
     */
    public static SqlSession openSession(String dbPath) {
        return getOrCreate(dbPath).openSession();
    }

    /**
     * Open a new SqlSession with auto-commit control.
     */
    public static SqlSession openSession(String dbPath, boolean autoCommit) {
        return getOrCreate(dbPath).openSession(autoCommit);
    }

    private static SqlSessionFactory buildFactory(String dbPath) {
        // High-performance connection URL with connection-level SQLite PRAGMAs
        String jdbcUrl = "jdbc:sqlite:" + dbPath + "?"
                + "journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&cache_size=-64000"       // 64MB memory cache
                + "&mmap_size=268435456"     // 256MB memory-mapped I/O
                + "&temp_store=MEMORY"       // Memory temporary tables
                + "&busy_timeout=10000";     // 10s busy timeout for concurrent read/write

        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.sqlite.JDBC",
                jdbcUrl,
                null,
                null
        );

        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("sim-" + dbPath, transactionFactory, dataSource);

        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false); // Disable L2 cache for simulation workloads

        // Register type aliases
        configuration.getTypeAliasRegistry().registerAliases(
                "jp.ac.tsukuba.eclab.assetmarketsimulation.entity");

        // Register all mapper interfaces
        configuration.addMapper(SchemaMapper.class);
        configuration.addMapper(SimulationMetaMapper.class);
        configuration.addMapper(SectorMapper.class);
        configuration.addMapper(StockMapper.class);
        configuration.addMapper(AgentMapper.class);
        configuration.addMapper(MarketDailyMapper.class);
        configuration.addMapper(SectorDailyMapper.class);
        configuration.addMapper(AgentTypeDailyMapper.class);
        configuration.addMapper(StockDailyMapper.class);
        configuration.addMapper(AgentAssetDailyMapper.class);
        configuration.addMapper(HoldingsMapper.class);
        configuration.addMapper(TradeRecordMapper.class);
        configuration.addMapper(EventLogMapper.class);

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * Initialize the database schema for a new simulation.
     */
    public static void initializeSchema(SqlSessionFactory factory) {
        try {
            // ====================================================
            // Step 1: Execute PRAGMAs on a separate auto-commit connection.
            // ====================================================
            try (SqlSession pragmaSession = factory.openSession(true)) { // autoCommit = true
                Connection conn = pragmaSession.getConnection();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("PRAGMA cache_size = -64000");
                    stmt.execute("PRAGMA mmap_size = 268435456");
                    stmt.execute("PRAGMA temp_store = MEMORY");
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }

            // Step 2: Create tables and indexes via MyBatis mapper
            try (SqlSession session = factory.openSession()) {
                SchemaMapper schema = session.getMapper(SchemaMapper.class);
                schema.createSimulationMetaTable();
                schema.createSectorTable();
                schema.createStockTable();
                schema.createAgentTable();
                schema.createMarketDailyTable();
                schema.createSectorDailyTable();
                schema.createAgentTypeDailyTable();
                schema.createStockDailyTable();
                schema.createAgentAssetDailyTable();
                schema.createHoldingsSnapshotTable();
                schema.createHoldingsDeltaTable();
                schema.createTradeRecordTable();
                schema.createEventLogTable();
                schema.createLeverageRecordTable();

                schema.createIndexStockDailyDay();
                schema.createIndexStockDailyStock();
                schema.createIndexStockDailyDayStock();
                schema.createIndexSectorDailyDay();
                schema.createIndexSectorDailyName();
                schema.createIndexAgentTypeDailyDay();
                schema.createIndexAgentTypeDailyType();
                schema.createIndexAgentAssetDay();
                schema.createIndexAgentAssetActive();
                schema.createIndexTradeRecordDay();
                schema.createIndexTradeRecordStock();
                schema.createIndexTradeRecordBuyer();
                schema.createIndexTradeRecordSeller();
                schema.createIndexHoldingsSnapshotAgentDay();
                schema.createIndexHoldingsDeltaDay();
                schema.createIndexHoldingsDeltaAgentDay();
                schema.createIndexStockSectorId();
                schema.createIndexAgentAgentType();
                schema.createIndexEventLogDay();

                session.commit();
            }

            System.out.println("Database schema initialized successfully.");
        } catch (Exception e) {
            System.err.println("FATAL: Schema initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Schema initialization failed", e);
        }
    }
}
