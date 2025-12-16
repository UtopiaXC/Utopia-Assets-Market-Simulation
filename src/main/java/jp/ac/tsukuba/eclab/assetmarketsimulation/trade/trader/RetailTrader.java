package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import sim.engine.SimState;
import sim.util.Bag;

public class RetailTrader extends BaseTrader {

    private class StockScore {
        Stock stock;
        double score;
        StockScore(Stock stock, double score) {
            this.stock = stock;
            this.score = score;
        }
    }

    private static final double ORDER_PRICE_BUFFER = 0.02;

    private final double fundamentalWeight;
    private final double trendWeight;
    private final double noiseStdDev;
    private final int lookbackDays;

    // 【新增 V4.33】 是否已经完成保本操作
    private boolean hasSecuredPrincipal = false;

    public RetailTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "RETAIL", initialCash, riskTolerance, maxStocks,
                Config.AGENT_RETAIL_MAX_STOCKS_MIN,
                Config.AGENT_RETAIL_MAX_STOCKS_MAX,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS);

        this.fundamentalWeight = Config.AGENT_RETAIL_VALUATION_FUNDAMENTAL_WEIGHT;
        this.trendWeight = Config.AGENT_RETAIL_VALUATION_TREND_WEIGHT;
        this.noiseStdDev = Config.AGENT_RETAIL_VALUATION_NOISE_STDDEV;
        this.lookbackDays = Config.AGENT_RETAIL_VALUATION_LOOKBACK_DAYS;
    }

    @Override
    public double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model) {
        return 0;
    }

    /**
     * 【新增 V4.33】 散户生命周期逻辑：撤资检查
     */
    @Override
    public double checkWithdrawal() {
        if (!this.isActive()) return 0;

        double withdrawAmount = 0;

        // 1. 保本阶段：如果还没有保本，且场内现金足以覆盖本金的 105%
        if (!hasSecuredPrincipal) {
            if (portfolio.cash > initialCapitalRecorded * Config.AGENT_RETAIL_PRINCIPAL_SECURE_BUFFER) {
                // 撤出所有本金
                withdrawAmount = initialCapitalRecorded;
                hasSecuredPrincipal = true;
            }
        }
        // 2. 利润收割阶段：如果已经保本（用利润在玩），但现金积累太多，抽水一部分
        else {
            double totalStockValue = portfolio.getTotalStockValue();
            double totalAssets = portfolio.cash + totalStockValue; // 不含 savings

            // 如果现金占比超过 trigger (例如 50%)，且绝对值大于 1万 (避免小碎钱)
            if (portfolio.cash > totalAssets * Config.AGENT_RETAIL_PROFIT_SKIM_TRIGGER && portfolio.cash > 10000) {
                // 移出当前现金的 ratio (例如 20%)
                withdrawAmount = portfolio.cash * Config.AGENT_RETAIL_PROFIT_SKIM_RATIO;
            }
        }
        return withdrawAmount;
    }

    /**
     * 【新增 V4.33】 散户生命周期逻辑：破产检查
     */
    @Override
    public boolean isBankrupt() {
        if (!this.isActive()) return false;

        // 如果已经保本了，理论上心态好，可以容忍归零 (或者设置一个极低的线)，这里暂不强制保本者离场。
        // 主要针对未保本的：
        if (!hasSecuredPrincipal) {
            double totalAssets = portfolio.getTotalAssets();
            // 如果资产缩水到只剩阈值 (例如 20%) -> 绝望离场
            if (totalAssets < initialCapitalRecorded * Config.AGENT_RETAIL_DESPAIR_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void step(SimState state) {
        StockMarketSim model = (StockMarketSim) state;
        long currentStep = state.schedule.getSteps();
        if (currentStep < super.nextTradeStep) return;
        if (!model.market.isTradingHours()) return;
        doRebalance(model);
        super.setNextTradeStep(model);
    }

    private void doRebalance(StockMarketSim model) {
        List<StockScore> allStockScores = new ArrayList<>();
        Bag allStocks = model.stocks;
        for (int i = 0; i < allStocks.size(); i++) {
            Stock stock = (Stock) allStocks.get(i);
            if (stock.currentPrice <= 0) continue;

            double fundamentalValue = model.valuation.calculateFundamentalValue(stock);
            double trendValue = model.market.getPriceTrend(stock, this.lookbackDays);

            // 散户更看重趋势 (50% Trend)
            double combinedValue = (fundamentalValue * this.fundamentalWeight) + (trendValue * this.trendWeight);
            double noise = 1.0 + (model.random.nextGaussian() * this.noiseStdDev);
            double finalValue = combinedValue * noise;

            double score = finalValue / stock.currentPrice;
            allStockScores.add(new StockScore(stock, score));
        }

        Collections.sort(allStockScores, Comparator.comparingDouble((StockScore o) -> o.score).reversed());

        // 1. 卖出逻辑：只卖高估的
        Set<Stock> heldStocks = new HashSet<>(portfolio.getPositions().keySet());
        for (Stock heldStock : heldStocks) {
            double availableQty = portfolio.getAvailableQuantity(heldStock);
            if (availableQty <= 0) continue;

            double myScore = 0;
            for(StockScore ss : allStockScores) {
                if(ss.stock == heldStock) { myScore = ss.score; break; }
            }

            // 散户如果觉得贵了 (Score < 0.95) 就卖
            if (myScore < 0.95) {
                model.market.submitSellOrder(this, heldStock, availableQty, heldStock.currentPrice * (1.0 - ORDER_PRICE_BUFFER));
            }
        }

        // 2. 买入逻辑
        int buyCount = 0;
        for (StockScore candidate : allStockScores) {
            if (buyCount >= 2) break; // 散户钱少，只买前2名
            if (candidate.score < 1.05) break;

            Stock targetStock = candidate.stock;
            double investAmount = portfolio.cash * 0.20; // 激进一点，投入20%现金

            if (investAmount < targetStock.currentPrice * 100) continue;

            double qtyToBuy = Math.floor(investAmount / targetStock.currentPrice / 100) * 100;
            if (qtyToBuy > 0 && canBuyNewStock()) {
                double limitPrice = targetStock.currentPrice * (1.0 + ORDER_PRICE_BUFFER);
                if (portfolio.reserveCash(qtyToBuy * limitPrice)) {
                    model.market.submitBuyOrder(this, targetStock, qtyToBuy, limitPrice);
                    buyCount++;
                }
            }
        }
    }

    @Override protected Stock chooseStock(StockMarketSim model) { return null; }
    @Override protected void makeDecision(StockMarketSim model, Stock stock) {}
}