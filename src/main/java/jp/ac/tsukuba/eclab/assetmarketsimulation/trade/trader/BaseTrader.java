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

    @Override
    public void step(SimState state) {
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

    protected void setNextTradeStep(StockMarketSim model) {
        int stepsPerDay = model.market.STEPS_PER_DAY;
        int daysToWait = model.random.nextInt(tradeIntervalMaxDays - tradeIntervalMinDays + 1) + tradeIntervalMinDays;
        this.nextTradeStep = model.schedule.getSteps() + (long) daysToWait * stepsPerDay;
    }

    protected abstract Stock chooseStock(StockMarketSim model);
    protected abstract void makeDecision(StockMarketSim model, Stock stock);

    // --- 【【V4.25 恢复】】 恢复 V4.21 的 IPO 逻辑 ---
    public abstract double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model);

    // (V4.24 方法已移除)
    // public abstract double getIPOTotalAllocation();
    // public abstract double getIPOInterestMultiplier(Stock stock);
    // --- 【【V4.25 恢复结束】】 ---

    public void mutateTraits(SimState state) {
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