package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import org.apache.ibatis.annotations.Update;

/**
 * Schema initialization mapper - creates all tables and indexes.
 * Executed once when a new simulation database is created.
 */
public interface SchemaMapper {

    @Update("CREATE TABLE IF NOT EXISTS simulation_meta (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "seed BIGINT, " +
            "start_time TEXT, " +
            "scenario_name TEXT, " +
            "num_stocks INT, " +
            "num_agents INT, " +
            "simulation_days INT, " +
            "steps_per_day INT, " +
            "config_json TEXT)")
    void createSimulationMetaTable();

    @Update("CREATE TABLE IF NOT EXISTS sector (" +
            "id INTEGER PRIMARY KEY, " +
            "name TEXT NOT NULL UNIQUE, " +
            "display_name TEXT)")
    void createSectorTable();

    @Update("CREATE TABLE IF NOT EXISTS stock (" +
            "id INTEGER PRIMARY KEY, " +
            "stock_code TEXT NOT NULL UNIQUE, " +
            "sector_id INTEGER NOT NULL, " +
            "ipo_price REAL, " +
            "total_shares REAL, " +
            "liquid_shares REAL, " +
            "initial_net_assets REAL, " +
            "initial_eps REAL, " +
            "earnings_growth REAL, " +
            "beta REAL, " +
            "FOREIGN KEY (sector_id) REFERENCES sector(id))")
    void createStockTable();

    @Update("CREATE TABLE IF NOT EXISTS agent (" +
            "id INTEGER PRIMARY KEY, " +
            "agent_type TEXT NOT NULL, " +
            "initial_cash REAL, " +
            "max_stocks INT, " +
            "initial_risk_tolerance REAL)")
    void createAgentTable();

    @Update("CREATE TABLE IF NOT EXISTS market_daily (" +
            "day INTEGER PRIMARY KEY, " +
            "open REAL, high REAL, low REAL, close REAL, " +
            "volume REAL, turnover REAL, " +
            "total_market_cap REAL, " +
            "amplitude REAL, turnover_rate REAL, " +
            "social_wealth_pool REAL, " +
            "active_agents INT, " +
            "circuit_breaker_triggered BOOLEAN DEFAULT 0)")
    void createMarketDailyTable();

    @Update("CREATE TABLE IF NOT EXISTS stock_daily (" +
            "day INTEGER NOT NULL, " +
            "stock_id INTEGER NOT NULL, " +
            "open REAL, high REAL, low REAL, close REAL, " +
            "volume REAL, turnover REAL, " +
            "pb_ratio REAL, pe_ttm REAL, pe_dynamic REAL, pe_static REAL, " +
            "eps REAL, net_assets REAL, " +
            "total_market_cap REAL, liquid_market_cap REAL, " +
            "turnover_rate REAL, amplitude REAL, " +
            "high_52w REAL, low_52w REAL, " +
            "PRIMARY KEY (day, stock_id), " +
            "FOREIGN KEY (stock_id) REFERENCES stock(id))")
    void createStockDailyTable();

    @Update("CREATE TABLE IF NOT EXISTS agent_asset_daily (" +
            "day INTEGER NOT NULL, " +
            "agent_id INTEGER NOT NULL, " +
            "cash REAL, reserved_cash REAL, " +
            "private_savings REAL, stock_value REAL, " +
            "total_assets REAL, risk_tolerance REAL, " +
            "borrowed_cash REAL DEFAULT 0, " +
            "net_equity REAL DEFAULT 0, " +
            "margin_ratio REAL DEFAULT 0, " +
            "is_active BOOLEAN, " +
            "PRIMARY KEY (day, agent_id), " +
            "FOREIGN KEY (agent_id) REFERENCES agent(id))")
    void createAgentAssetDailyTable();

    @Update("CREATE TABLE IF NOT EXISTS holdings_snapshot (" +
            "snapshot_day INTEGER NOT NULL, " +
            "agent_id INTEGER NOT NULL, " +
            "holdings_json TEXT, " +
            "PRIMARY KEY (snapshot_day, agent_id))")
    void createHoldingsSnapshotTable();

    @Update("CREATE TABLE IF NOT EXISTS holdings_delta (" +
            "day INTEGER NOT NULL, " +
            "agent_id INTEGER NOT NULL, " +
            "stock_id INTEGER NOT NULL, " +
            "quantity_change REAL, " +
            "PRIMARY KEY (day, agent_id, stock_id))")
    void createHoldingsDeltaTable();

    @Update("CREATE TABLE IF NOT EXISTS trade_record (" +
            "day INTEGER NOT NULL, " +
            "stock_id INTEGER NOT NULL, " +
            "buyer_id INTEGER NOT NULL, " +
            "seller_id INTEGER NOT NULL, " +
            "price REAL NOT NULL, " +
            "quantity REAL NOT NULL, " +
            "influence_json TEXT, " +
            "FOREIGN KEY (stock_id) REFERENCES stock(id), " +
            "FOREIGN KEY (buyer_id) REFERENCES agent(id), " +
            "FOREIGN KEY (seller_id) REFERENCES agent(id))")
    void createTradeRecordTable();

    @Update("CREATE TABLE IF NOT EXISTS event_log (" +
            "event_id TEXT PRIMARY KEY, " +
            "day INTEGER, " +
            "event_type TEXT, " +
            "source TEXT, " +
            "parameters_json TEXT, " +
            "description TEXT)")
    void createEventLogTable();

    @Update("CREATE TABLE IF NOT EXISTS leverage_record (" +
            "day INTEGER NOT NULL, " +
            "agent_id INTEGER NOT NULL, " +
            "borrowed_amount REAL, " +
            "total_assets REAL, " +
            "margin_ratio REAL, " +
            "event_type TEXT, " +
            "PRIMARY KEY (day, agent_id))")
    void createLeverageRecordTable();

    // Indexes for query performance
    @Update("CREATE INDEX IF NOT EXISTS idx_stock_daily_day ON stock_daily(day)")
    void createIndexStockDailyDay();

    @Update("CREATE INDEX IF NOT EXISTS idx_stock_daily_stock ON stock_daily(stock_id)")
    void createIndexStockDailyStock();

    @Update("CREATE INDEX IF NOT EXISTS idx_agent_asset_day ON agent_asset_daily(day)")
    void createIndexAgentAssetDay();

    @Update("CREATE INDEX IF NOT EXISTS idx_agent_asset_active ON agent_asset_daily(day, is_active)")
    void createIndexAgentAssetActive();

    @Update("CREATE INDEX IF NOT EXISTS idx_trade_record_day ON trade_record(day)")
    void createIndexTradeRecordDay();

    @Update("CREATE INDEX IF NOT EXISTS idx_trade_record_stock ON trade_record(stock_id, day)")
    void createIndexTradeRecordStock();

    @Update("CREATE INDEX IF NOT EXISTS idx_trade_record_buyer ON trade_record(buyer_id, day)")
    void createIndexTradeRecordBuyer();

    @Update("CREATE INDEX IF NOT EXISTS idx_trade_record_seller ON trade_record(seller_id, day)")
    void createIndexTradeRecordSeller();

    @Update("CREATE INDEX IF NOT EXISTS idx_holdings_delta_day ON holdings_delta(day)")
    void createIndexHoldingsDeltaDay();

    @Update("CREATE INDEX IF NOT EXISTS idx_event_log_day ON event_log(day)")
    void createIndexEventLogDay();

    // NOTE: SQLite PRAGMAs are executed via raw JDBC in DynamicSqlSessionManager
    // because PRAGMA journal_mode returns a result set, incompatible with @Update.
}
