package jp.ac.tsukuba.eclab.assetmarketsimulation;

import java.util.concurrent.ThreadLocalRandom;

public final class Config {

    private Config() {}

    // 1. 市场 (Market) 参数
    public static final int MARKET_NUM_STOCKS = 50;
    public static final int MARKET_SIMULATION_DAYS = 1000;
    public static final double MARKET_INDEX_BASE = 3000.0;
    public static final double MARKET_PRICE_LIMIT_RATIO = 0.10;

    // 2. 时间 (Time) 参数
    public static final int MARKET_STEPS_PER_DAY = 22;
    public static final int MARKET_LUNCH_BREAK_START = 8;
    public static final int MARKET_LUNCH_BREAK_END = 14;
    public static final int MARKET_STEPS_PER_QUARTER = 1386;

    // 3. 股票 (Stock) 基础参数
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

    // 4. 经济体顶层设计
    public static final double ECONOMY_TOTAL_CAPITAL_POOL = 3.5e11;
    public static final int ECONOMY_TOTAL_AGENTS = 5000;

    // 【新增 V4.33】 宏观与生命周期参数
    public static final int ECONOMY_TARGET_POPULATION = 5000;
    public static final double ECONOMY_SOCIAL_POOL_RATIO = 2.0; // 初始社会池是场内资金的2倍
    public static final double ECONOMY_FOMO_SENSITIVITY = 5.0;  // 市场每涨1%，生成概率增加多少
    public static final double ECONOMY_BASE_ENTRY_PROB = 0.01;  // 缺人时的基础补充概率

    // 5-7. Agent 基础比例与参数
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

    // 8. 行为参数
    public static final double AGENT_MUTATION_RATE = 0.01;
    public static final double AGENT_MUTATION_STDDEV = 0.05;

    // 机构行为
    public static final double AGENT_INSTITUTIONAL_IPO_GOOD_STOCK_PERCENT = 0.10;
    public static final double AGENT_INSTITUTIONAL_IPO_OK_STOCK_PERCENT = 0.10;
    public static final double AGENT_INSTITUTIONAL_VALUATION_FUNDAMENTAL_WEIGHT = 0.8;
    public static final double AGENT_INSTITUTIONAL_VALUATION_TREND_WEIGHT = 0.2;
    public static final double AGENT_INSTITUTIONAL_VALUATION_NOISE_STDDEV = 0.05;
    public static final int AGENT_INSTITUTIONAL_VALUATION_LOOKBACK_DAYS = 20;
    public static final double AGENT_INSTITUTIONAL_BANKRUPTCY_THRESHOLD = 0.10; // 资产剩10%清算

    // 散户行为
    public static final double AGENT_RETAIL_IPO_HOT_SECTOR_PERCENT = 0.10;
    public static final double AGENT_RETAIL_IPO_NORMAL_PERCENT = 0.10;
    public static final double AGENT_RETAIL_VALUATION_FUNDAMENTAL_WEIGHT = 0.5;
    public static final double AGENT_RETAIL_VALUATION_TREND_WEIGHT = 0.5;
    public static final double AGENT_RETAIL_VALUATION_NOISE_STDDEV = 0.10;
    public static final int AGENT_RETAIL_VALUATION_LOOKBACK_DAYS = 10;

    // 【新增 V4.33】 散户进出场参数
    public static final double AGENT_RETAIL_DESPAIR_THRESHOLD = 0.20; // 亏损到剩20%离场
    public static final double AGENT_RETAIL_PRINCIPAL_SECURE_BUFFER = 1.05; // 盈利5%后开始抽走本金
    public static final double AGENT_RETAIL_PROFIT_SKIM_TRIGGER = 0.50; // 现金占总资产50%时触发抽水
    public static final double AGENT_RETAIL_PROFIT_SKIM_RATIO = 0.20; // 每次抽走超额现金的20%

    // 噪音行为
    public static final double AGENT_NOISE_IPO_MIN_PERCENT = 0.10;
    public static final double AGENT_NOISE_IPO_MAX_PERCENT = 0.10;
    public static final double AGENT_NOISE_VALUATION_NOISE_STDDEV = 0.05;

    // 9. 估值 (Valuation) 服务参数
    public static final double VALUATION_PB_MULTIPLIER = 1.5;
    public static final double VALUATION_PE_MULTIPLIER = 25.0;
    public static final double VALUATION_SECTOR_TECH = 1.3;
    public static final double VALUATION_SECTOR_HEALTHCARE = 1.2;
    public static final double VALUATION_SECTOR_CONSUMER = 1.1;
    public static final double VALUATION_SECTOR_FINANCE = 0.9;
    public static final double VALUATION_SECTOR_INDUSTRY = 1.0;

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