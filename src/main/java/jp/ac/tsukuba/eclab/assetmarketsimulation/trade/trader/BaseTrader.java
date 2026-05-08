package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Portfolio;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.HashMap;
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