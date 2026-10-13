package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Portfolio;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.SocialNetwork;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class BaseTrader implements Steppable {

    public final int traderId;
    public final String traderType;
    public Portfolio portfolio;

    public double riskTolerance;
    public int maxStocks;

    // 社交网络参数 (Slides Page 4 & 8)
    public double socialSensitivity; // β: Social Sensitivity
    public int topKNeighbors;        // K: Top Neighbors Count

    // 信念向量 φ_A: 每只股票的信念价格
    private Map<Stock, Double> beliefs = new HashMap<>();

    // 最新一次计算得到的社交影响 JSON 字符串
    private String lastInfluenceJson = null;

    // 个人场外储蓄 (Private Savings)
    public double privateSavings = 0;

    // 初始投入记录 (用于计算保本和盈亏)
    public double initialCapitalRecorded;

    // 活跃状态标记 (false 表示已破产或离场)
    private boolean isActive = true;

    private final double mutationRate;
    private final double mutationStdDev;

    protected int maxStocksLimit;
    protected int minStocksLimit;

    protected long nextTradeStep;
    private final int tradeIntervalMinDays;
    private final int tradeIntervalMaxDays;

    public BaseTrader(int id, String type, double initialCash, double riskTolerance,
                      int maxStocks, int minStocksLimit, int maxStocksLimit,
                      int tradeIntervalMinDays, int tradeIntervalMaxDays) {

        this.traderId = id;
        this.traderType = type;
        this.portfolio = new Portfolio(initialCash);

        this.initialCapitalRecorded = initialCash;
        this.riskTolerance = riskTolerance;
        this.maxStocks = maxStocks;
        this.minStocksLimit = minStocksLimit;
        this.maxStocksLimit = maxStocksLimit;
        this.tradeIntervalMinDays = tradeIntervalMinDays;
        this.tradeIntervalMaxDays = tradeIntervalMaxDays;
        this.nextTradeStep = 0;
        this.mutationRate = Config.AGENT_MUTATION_RATE;
        this.mutationStdDev = Config.AGENT_MUTATION_STDDEV;

        // 默认社交网络参数
        this.socialSensitivity = Config.SOCIAL_SENSITIVITY_BETA;
        this.topKNeighbors = Config.SOCIAL_TOP_K_NEIGHBORS;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    /**
     * 获取对某股票的当前信念价格
     */
    public double getBelief(Stock stock) {
        return beliefs.getOrDefault(stock, stock.currentPrice);
    }

    /**
     * 更新对某股票的信念
     */
    public void setBelief(Stock stock, double belief) {
        beliefs.put(stock, belief);
    }

    public String getLastInfluenceJson() {
        return lastInfluenceJson;
    }

    public void setLastInfluenceJson(String json) {
        this.lastInfluenceJson = json;
    }

    /**
     * 生成格式化的社交影响 JSON 字符串 (兼容旧版前端字段与新版拓展)
     */
    public String buildInfluenceJson(Stock stock, List<SocialNetwork.NeighborEntry> entries) {
        if (entries == null || entries.isEmpty()) return "[]";
        StringBuilder json = new StringBuilder("[");
        for (int j = 0; j < entries.size(); j++) {
            SocialNetwork.NeighborEntry entry = entries.get(j);
            json.append(String.format(
                    Locale.US,
                    "{\"neighborId\":%d,\"type\":\"%s\",\"similarity\":%.4f,\"reputation\":%.4f,\"weight\":%.4f,\"belief\":%.4f}%s",
                    entry.neighbor.traderId,
                    entry.isInstitutional ? "INSTITUTIONAL" : "PEER",
                    entry.similarity,
                    entry.reputation,
                    entry.weight,
                    entry.neighbor.getBelief(stock),
                    (j < entries.size() - 1) ? "," : ""
            ));
        }
        json.append("]");
        return json.toString();
    }

    // 净资产与历史业绩追踪 (用于动态信誉评分)
    private double lastDayNav = -1.0;
    private double meanAlpha = 0.0;
    private double varianceAlpha = 0.0;
    private double reputationScore = 0.0; // R_{k,t} in [-1, 1]
    private int activeDaysCount = 0;

    /**
     * 计算当前时刻的总净资产价值 (NAV)
     * NAV = 净权益 (含股票市值、现金、冻结现金 - 借款) + 场外储蓄
     */
    public double calculateCurrentNav() {
        return this.portfolio.getNetEquity() + this.privateSavings;
    }

    public double getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(double score) {
        this.reputationScore = score;
    }

    public double getLastDayNav() {
        return lastDayNav;
    }

    public void setLastDayNav(double nav) {
        this.lastDayNav = nav;
    }

    public int getActiveDaysCount() {
        return activeDaysCount;
    }

    /**
     * 收盘后更新每日超额收益与动态信誉 (在 T 日结束时执行，T+1 生效，杜绝前瞻偏差)
     *
     * @param marketReturn 当日市场基准收益率 r_{m,t}
     */
    public void updateDailyReputation(double marketReturn) {
        if (!isActive) return;

        double currentNav = calculateCurrentNav();

        if (lastDayNav <= 0) {
            lastDayNav = currentNav;
            activeDaysCount = 1;
            reputationScore = 0.0;
            return;
        }

        // 1. 机构 NAV 收益率: r_{k,t} = (NAV_t - NAV_{t-1}) / NAV_{t-1}
        double agentReturn = (currentNav - lastDayNav) / lastDayNav;
        lastDayNav = currentNav;
        activeDaysCount++;

        // 2. 相对超额收益率: alpha_{k,t} = r_{k,t} - r_{m,t}
        double alpha = agentReturn - marketReturn;

        // 3. EMA 估计近期的超额收益均值与方差
        double lambdaM = Config.SOCIAL_ALPHA_EMA_LAMBDA;
        double lambdaV = Config.SOCIAL_VARIANCE_EMA_LAMBDA;

        if (activeDaysCount <= 2) {
            meanAlpha = alpha;
            varianceAlpha = 0.0001;
        } else {
            double prevMean = meanAlpha;
            meanAlpha = lambdaM * meanAlpha + (1.0 - lambdaM) * alpha;
            varianceAlpha = lambdaV * varianceAlpha + (1.0 - lambdaV) * (alpha - prevMean) * (alpha - prevMean);
        }

        // 4. 计算样本置信度 c_{k,t} in [0, 1] (避免新成立机构产生极端波动)
        double confidence = Math.min(1.0, (double) activeDaysCount / Config.SOCIAL_REPUTATION_WARMUP_DAYS);

        // 5. 风险调整后的有界信誉得分: R_{k,t} = c_{k,t} * tanh(kappa * meanAlpha / (sqrt(varianceAlpha) + epsilon))
        double epsilon = 1e-4;
        double kappa = Config.SOCIAL_REPUTATION_SCALE_KAPPA;
        double stdDev = Math.sqrt(Math.max(0, varianceAlpha));
        double informationRatio = meanAlpha / (stdDev + epsilon);

        this.reputationScore = confidence * Math.tanh(kappa * informationRatio);
    }

    /**
     * 获取所有信念
     */
    public Map<Stock, Double> getBeliefs() {
        return beliefs;
    }

    @Override
    public void step(SimState state) {
        if (!isActive) return;

        StockMarketSim model = (StockMarketSim) state;
        long currentStep = model.schedule.getSteps();
        if (currentStep < this.nextTradeStep) return;
        if (!model.market.isTradingHours()) return;

        Stock stockToEvaluate = chooseStock(model);
        if (stockToEvaluate == null) {
            setNextTradeStep(model);
            return;
        }
        makeDecision(model, stockToEvaluate);
        setNextTradeStep(model);
    }

    /**
     * 银证转账：从场内 (Portfolio) 转出到场外 (Savings)
     */
    public void transferToSavings(double amount) {
        if (amount <= 0) return;
        if (this.portfolio.cash >= amount) {
            this.portfolio.cash -= amount;
            this.privateSavings += amount;
        }
    }

    /**
     * 银证转账：从场外 (Savings) 转入到场内 (Portfolio)
     */
    public void transferFromSavings(double amount) {
        if (amount <= 0) return;
        if (this.privateSavings >= amount) {
            this.privateSavings -= amount;
            this.portfolio.cash += amount;
        }
    }

    /**
     * 检查是否需要进行资金撤出 (止盈/保本)
     */
    public double checkWithdrawal() {
        return 0;
    }

    /**
     * 检查是否破产/绝望离场
     */
    public boolean isBankrupt() {
        return false;
    }

    protected void setNextTradeStep(StockMarketSim model) {
        int stepsPerDay = model.market.STEPS_PER_DAY;
        int daysToWait = model.random.nextInt(tradeIntervalMaxDays - tradeIntervalMinDays + 1) + tradeIntervalMinDays;
        this.nextTradeStep = model.schedule.getSteps() + (long) daysToWait * stepsPerDay;
    }

    protected abstract Stock chooseStock(StockMarketSim model);
    protected abstract void makeDecision(StockMarketSim model, Stock stock);

    public void mutateTraits(SimState state) {
        if (!isActive) return;
        if (state.random.nextDouble() < mutationRate) {
            this.riskTolerance += state.random.nextGaussian() * mutationStdDev;
            if (this.riskTolerance > 1.0) this.riskTolerance = 1.0;
            if (this.riskTolerance < 0.0) this.riskTolerance = 0.0;
        }
        if (state.random.nextDouble() < mutationRate) {
            this.maxStocks += state.random.nextBoolean() ? 1 : -1;
            if (this.maxStocks > this.maxStocksLimit) this.maxStocks = this.maxStocksLimit;
            if (this.maxStocks < this.minStocksLimit) this.maxStocks = this.minStocksLimit;
        }
    }

    protected boolean canBuyNewStock() {
        return portfolio.getPositions().size() < this.maxStocks;
    }
}