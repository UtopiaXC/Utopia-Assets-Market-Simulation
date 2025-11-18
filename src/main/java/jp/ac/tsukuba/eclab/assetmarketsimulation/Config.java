package jp.ac.tsukuba.eclab.assetmarketsimulation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 静态配置类 (V4.26 - 恢复 V4.24 的混合估值模型)
 */
public final class Config {

    private Config() {}

    // 1. 市场 (Market) 参数
    public static final int MARKET_NUM_STOCKS = 20;
    public static final int MARKET_SIMULATION_DAYS = 250;
    public static final double MARKET_INDEX_BASE = 3000.0;

    // 2. 时间 (Time) 参数
    public static final int MARKET_STEPS_PER_DAY = 22;
    public static final int MARKET_LUNCH_BREAK_START = 8;
    public static final int MARKET_LUNCH_BREAK_END = 14;
    public static final int MARKET_STEPS_PER_QUARTER = 1386;

    // 3. 股票 (Stock) 基础参数 (IPO 时)
    public static final double[] STOCK_IPO_PRICE = {2.0, 1.0, 1.0, 10.0};
    public static final double[] STOCK_IPO_NET_ASSETS = {1.0, 0.5, 0.5, 5.0};
    public static final double[] STOCK_IPO_EPS = {0.1, 0.1, -0.5, 1.0};
    public static final double[] STOCK_IPO_LIQUID_SHARES = {2.5e7, 1.0e7, 1.0e7, 5.0e7};

    public static final double STOCK_FUNDAMENTALS_GROWTH_RATE_MIN = -0.05;
    public static final double STOCK_FUNDAMENTALS_GROWTH_RATE_MAX = 0.15;
    public static final double STOCK_FUNDAMENTALS_BETA_MIN = 0.5;
    public static final double STOCK_FUNDAMENTALS_BETA_MAX = 2.0;
    public static final double STOCK_FUNDAMENTALS_EPS_VOLATILITY = 0.15;
    public static final double STOCK_FUNDAMENTALS_RETAINED_EARNINGS_RATIO = 0.70;


    // 4. 【【V4.21 经济体顶层设计】】
    public static final double ECONOMY_TOTAL_CAPITAL_POOL = 3.0e10; // 300 亿
    public static final int ECONOMY_TOTAL_AGENTS = 5000;

    // 5. 【【V4.20 资本和人口分配】】
    public static final double AGENT_INSTITUTIONAL_POPULATION_RATIO = 0.05; // 5%
    public static final double AGENT_INSTITUTIONAL_CAPITAL_RATIO = 0.70;    // 70%
    public static final double AGENT_RETAIL_SUB_RATIO = 0.60;
    public static final double AGENT_NOISE_SUB_RATIO = 0.40;
    public static final double AGENT_INSTITUTIONAL_CASH_STDDEV_RATIO = 0.20;
    public static final double AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO = 0.80;

    // 6. 【【V4.20 持股上限】】
    public static final int AGENT_INSTITUTIONAL_MAX_STOCKS_MIN = 10;
    public static final int AGENT_INSTITUTIONAL_MAX_STOCKS_MAX = 30;
    public static final int AGENT_RETAIL_MAX_STOCKS_MIN = 1;
    public static final int AGENT_RETAIL_MAX_STOCKS_MAX = 15;
    public static final int AGENT_NOISE_MAX_STOCKS_MIN = 1;
    public static final int AGENT_NOISE_MAX_STOCKS_MAX = 15;

    // 7. 【【V4.20 交易频率 (Cooldown)】】
    public static final int AGENT_INSTITUTIONAL_TRADE_INTERVAL_MIN_DAYS = 3;
    public static final int AGENT_INSTITUTIONAL_TRADE_INTERVAL_MAX_DAYS = 5;
    public static final int AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS = 1;
    public static final int AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS = 3;
    public static final int AGENT_NOISE_TRADE_INTERVAL_MIN_DAYS = 1;
    public static final int AGENT_NOISE_TRADE_INTERVAL_MAX_DAYS = 3;

    // 8. 行为参数
    public static final double AGENT_MUTATION_RATE = 0.01;
    public static final double AGENT_MUTATION_STDDEV = 0.05;

    // 8a. 机构 (Institutional) 行为
    // (V4.25 IPO 逻辑 - 保持不变)
    public static final double AGENT_INSTITUTIONAL_IPO_GOOD_STOCK_PERCENT = 0.10;
    public static final double AGENT_INSTITUTIONAL_IPO_OK_STOCK_PERCENT = 0.10;

    // 【【V4.26 恢复】】 (V4.22/V4.24 混合估值)
    public static final double AGENT_INSTITUTIONAL_VALUATION_FUNDAMENTAL_WEIGHT = 0.8;
    public static final double AGENT_INSTITUTIONAL_VALUATION_TREND_WEIGHT = 0.2;
    public static final double AGENT_INSTITUTIONAL_VALUATION_NOISE_STDDEV = 0.05;
    public static final int AGENT_INSTITUTIONAL_VALUATION_LOOKBACK_DAYS = 20;

    // 8b. 散户 (Retail) 行为
    // (V4.25 IPO 逻辑 - 保持不变)
    public static final double AGENT_RETAIL_IPO_HOT_SECTOR_PERCENT = 0.10;
    public static final double AGENT_RETAIL_IPO_NORMAL_PERCENT = 0.10;

    // 【【V4.26 恢复】】 (V4.22/V4.24 混合估值)
    public static final double AGENT_RETAIL_VALUATION_FUNDAMENTAL_WEIGHT = 0.5;
    public static final double AGENT_RETAIL_VALUATION_TREND_WEIGHT = 0.5;
    public static final double AGENT_RETAIL_VALUATION_NOISE_STDDEV = 0.10;
    public static final int AGENT_RETAIL_VALUATION_LOOKBACK_DAYS = 10;

    // 8c. 噪音 (Noise) 行为
    // (V4.25 IPO 逻辑 - 保持不变)
    public static final double AGENT_NOISE_IPO_MIN_PERCENT = 0.10;
    public static final double AGENT_NOISE_IPO_MAX_PERCENT = 0.10;
    public static final double AGENT_NOISE_VALUATION_NOISE_STDDEV = 0.05;

    // 9. 估值 (Valuation) 服务参数
    public static final double VALUATION_PB_MULTIPLIER = 1.5;
    public static final double VALUATION_PE_MULTIPLIER = 20.0;
    public static final double VALUATION_SECTOR_TECH = 1.3;
    public static final double VALUATION_SECTOR_HEALTHCARE = 1.2;
    public static final double VALUATION_SECTOR_CONSUMER = 1.1;
    public static final double VALUATION_SECTOR_FINANCE = 0.9;
    public static final double VALUATION_SECTOR_INDUSTRY = 1.0;


    // (V4.20.1 辅助方法 - 保持不变)
    public static double nextGaussian(double mean, double stddev, double min, double max) {
        double value = mean + ThreadLocalRandom.current().nextGaussian() * stddev;
        if (value < min) value = min;
        if (value > max) value = max;
        return value;
    }
}