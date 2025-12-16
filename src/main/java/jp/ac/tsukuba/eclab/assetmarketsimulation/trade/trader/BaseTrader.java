package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Portfolio;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import sim.engine.SimState;
import sim.engine.Steppable;

public abstract class BaseTrader implements Steppable {

    public final int traderId;
    public final String traderType;
    public Portfolio portfolio;

    public double riskTolerance;
    public int maxStocks;

    // 【新增 V4.33】 个人场外储蓄 (Private Savings)
    // 这部分资金属于 Agent，但不参与交易，是安全的避风港。
    public double privateSavings = 0;

    // 【新增 V4.33】 初始投入记录 (用于计算保本和盈亏)
    public double initialCapitalRecorded;

    // 【新增 V4.33】 活跃状态标记 (false 表示已破产或离场)
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

        // 记录初始本金
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
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    @Override
    public void step(SimState state) {
        // 如果已不活跃，直接跳过
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
     * @return 需要转出到 privateSavings 的金额
     */
    public double checkWithdrawal() {
        return 0; // 默认不撤资
    }

    /**
     * 检查是否破产/绝望离场
     * @return true 表示需要离场
     */
    public boolean isBankrupt() {
        return false; // 默认不破产
    }

    protected void setNextTradeStep(StockMarketSim model) {
        int stepsPerDay = model.market.STEPS_PER_DAY;
        int daysToWait = model.random.nextInt(tradeIntervalMaxDays - tradeIntervalMinDays + 1) + tradeIntervalMinDays;
        this.nextTradeStep = model.schedule.getSteps() + (long) daysToWait * stepsPerDay;
    }

    protected abstract Stock chooseStock(StockMarketSim model);
    protected abstract void makeDecision(StockMarketSim model, Stock stock);

    public abstract double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model);

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