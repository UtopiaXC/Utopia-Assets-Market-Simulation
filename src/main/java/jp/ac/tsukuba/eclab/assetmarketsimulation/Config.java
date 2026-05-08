package jp.ac.tsukuba.eclab.assetmarketsimulation;

import java.util.concurrent.ThreadLocalRandom;

public final class Config {

    private Config() {}

    // ============================================================
    // 市场参数 / Market Config
    // ============================================================
    public static final int MARKET_NUM_STOCKS = 50;
    public static final int MARKET_SIMULATION_DAYS = 1000;
    public static final double MARKET_INDEX_BASE = 3000.0;

    // 时间参数 / Time Config
    public static final int MARKET_STEPS_PER_DAY = 22;
    public static final int MARKET_LUNCH_BREAK_START = 8;
    public static final int MARKET_LUNCH_BREAK_END = 14;
    public static final int MARKET_STEPS_PER_QUARTER = 1386;

    // ============================================================
    // 政策插槽默认值 / Policy Slot Defaults
    // P = [L_limit, Lev_max, Th_halt, N_settle]
    // ============================================================
    public static final double POLICY_PRICE_LIMIT_RATIO = 0.10;           // ±10%
    public static final double POLICY_CIRCUIT_BREAKER_THRESHOLD = 0.07;   // 7% index drop → halt
    public static final double POLICY_MAX_LEVERAGE_RATIO = 2.0;           // 2x leverage
    public static final int POLICY_SETTLEMENT_DAYS = 1;                   // T+1

    // ============================================================
    // 股票基础参数 / Stocks Config
    // ============================================================
    public static final double[] STOCK_IPO_PRICE = {3.2, 0.8, 0.1, 3000.0};
    public static final double[] STOCK_IPO_NET_ASSETS = {20.0, 10.0, 1.0, 100.0};
    public static final double[] STOCK_IPO_EPS = {2.2, 2.0, -5.0, 15.0};
    public static final double[] STOCK_IPO_LIQUID_SHARES = {1.0e8, 0.5e8, 0.2e8, 5.0e8};

    public static final double STOCK_FUNDAMENTALS_GROWTH_RATE_MIN = -0.05;
    public static final double STOCK_FUNDAMENTALS_GROWTH_RATE_MAX = 0.15;
    public static final double STOCK_FUNDAMENTALS_BETA_MIN = 0.5;
    public static final double STOCK_FUNDAMENTALS_BETA_MAX = 2.0;
    public static final double STOCK_FUNDAMENTALS_EPS_VOLATILITY = 0.15;
    public static final double STOCK_FUNDAMENTALS_RETAINED_EARNINGS_RATIO = 0.70;

    // ============================================================
    // 经济体参数 / Economy Config
    // ============================================================
    public static final double ECONOMY_TOTAL_CAPITAL_POOL = 3.5e11;
    public static final int ECONOMY_TOTAL_AGENTS = 5000;

    // 宏观与生命周期参数 / Lifecycle Config
    public static final int ECONOMY_TARGET_POPULATION = 5000;
    public static final double ECONOMY_SOCIAL_POOL_RATIO = 2.0;
    public static final double ECONOMY_FOMO_SENSITIVITY = 5.0;
    public static final double ECONOMY_BASE_ENTRY_PROB = 0.01;

    // ============================================================
    // Agents 基础比例与参数 / Agents Config
    // ============================================================
    public static final double AGENT_INSTITUTIONAL_POPULATION_RATIO = 0.05;
    public static final double AGENT_INSTITUTIONAL_CAPITAL_RATIO = 0.70;
    public static final double AGENT_RETAIL_SUB_RATIO = 0.60;
    public static final double AGENT_NOISE_SUB_RATIO = 0.40;
    public static final double AGENT_INSTITUTIONAL_CASH_STDDEV_RATIO = 0.20;
    public static final double AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO = 0.80;

    public static final int AGENT_INSTITUTIONAL_MAX_STOCKS_MIN = 20;
    public static final int AGENT_INSTITUTIONAL_MAX_STOCKS_MAX = 100;
    public static final int AGENT_RETAIL_MAX_STOCKS_MIN = 5;
    public static final int AGENT_RETAIL_MAX_STOCKS_MAX = 50;
    public static final int AGENT_NOISE_MAX_STOCKS_MIN = 5;
    public static final int AGENT_NOISE_MAX_STOCKS_MAX = 50;

    public static final int AGENT_INSTITUTIONAL_TRADE_INTERVAL_MIN_DAYS = 3;
    public static final int AGENT_INSTITUTIONAL_TRADE_INTERVAL_MAX_DAYS = 5;
    public static final int AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS = 1;
    public static final int AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS = 3;
    public static final int AGENT_NOISE_TRADE_INTERVAL_MIN_DAYS = 1;
    public static final int AGENT_NOISE_TRADE_INTERVAL_MAX_DAYS = 3;

    // 行为参数 / Behaviors Config
    public static final double AGENT_MUTATION_RATE = 0.01;
    public static final double AGENT_MUTATION_STDDEV = 0.05;

    // 机构行为 / Institutional Behaviors
    public static final double AGENT_INSTITUTIONAL_BANKRUPTCY_THRESHOLD = 0.10;

    // 散户进出场参数 / Retail Trader Lifecycle
    public static final double AGENT_RETAIL_DESPAIR_THRESHOLD = 0.20;
    public static final double AGENT_RETAIL_PRINCIPAL_SECURE_BUFFER = 1.05;
    public static final double AGENT_RETAIL_PROFIT_SKIM_TRIGGER = 0.50;
    public static final double AGENT_RETAIL_PROFIT_SKIM_RATIO = 0.20;

    // ============================================================
    // 估值模型参数 / Valuation Model Config (Slides Page 7)
    // ============================================================
    public static final double VALUATION_PB_MULTIPLIER = 1.5;   // M_PB
    public static final double VALUATION_PE_MULTIPLIER = 25.0;  // M_PE

    // 板块加成 / Sector bonus
    public static final double VALUATION_SECTOR_TECH = 1.3;
    public static final double VALUATION_SECTOR_HEALTHCARE = 1.2;
    public static final double VALUATION_SECTOR_CONSUMER = 1.1;
    public static final double VALUATION_SECTOR_FINANCE = 0.9;
    public static final double VALUATION_SECTOR_INDUSTRY = 1.0;
    public static final double VALUATION_SECTOR_ENERGY = 1.0;

    // 三因子权重 (Base weights per trader type)
    // Institutional: 基本面主导
    public static final double VALUATION_FUND_WEIGHT_INST = 0.80;
    public static final double VALUATION_SOCIAL_WEIGHT_INST = 0.10;
    public static final double VALUATION_TREND_WEIGHT_INST = 0.10;
    public static final double VALUATION_NOISE_STDDEV_INST = 0.05;
    public static final int VALUATION_LOOKBACK_DAYS_INST = 20;

    // Retail: 均衡
    public static final double VALUATION_FUND_WEIGHT_RETAIL = 0.50;
    public static final double VALUATION_SOCIAL_WEIGHT_RETAIL = 0.25;
    public static final double VALUATION_TREND_WEIGHT_RETAIL = 0.25;
    public static final double VALUATION_NOISE_STDDEV_RETAIL = 0.10;
    public static final int VALUATION_LOOKBACK_DAYS_RETAIL = 10;

    // Noise: 趋势 + 社交主导
    public static final double VALUATION_FUND_WEIGHT_NOISE = 0.10;
    public static final double VALUATION_SOCIAL_WEIGHT_NOISE = 0.30;
    public static final double VALUATION_TREND_WEIGHT_NOISE = 0.60;
    public static final double VALUATION_NOISE_STDDEV_NOISE = 0.20;
    public static final int VALUATION_LOOKBACK_DAYS_NOISE = 5;

    // ============================================================
    // 社交网络参数 / Social Network Config (Slides Page 8)
    // ============================================================
    public static final int SOCIAL_TOP_K_NEIGHBORS = 5;
    public static final double SOCIAL_SENSITIVITY_BETA = 0.3;
    public static final int SOCIAL_NETWORK_REBUILD_INTERVAL = 20; // days

    // ============================================================
    // 杠杆/配资参数 / Leverage Config
    // ============================================================
    public static final double LEVERAGE_MARGIN_CALL_RATIO = 1.30;    // 130% maintenance margin
    public static final double LEVERAGE_INTEREST_RATE_DAILY = 0.0003; // ~10% annually
    public static final double LEVERAGE_REQUEST_PROB = 0.05;          // 每次交易中请求配资的概率

    // ============================================================
    // Utility Methods
    // ============================================================

    public static double nextGaussian(double mean, double stddev, double min, double max) {
        double value = mean + ThreadLocalRandom.current().nextGaussian() * stddev;
        if (value < min) value = min;
        if (value > max) value = max;
        return value;
    }

    public static double nextLogNormal(double mu, double sigma, double min, double max) {
        double normalValue = ThreadLocalRandom.current().nextGaussian();
        double logValue = mu + normalValue * sigma;
        double value = Math.exp(logValue);
        if (value < min) value = min;
        if (value > max) value = max;
        return value;
    }
}