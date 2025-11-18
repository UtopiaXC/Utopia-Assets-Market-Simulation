package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import sim.engine.SimState;
import sim.util.Bag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * 【【V4.26 彻底重构】】
 * 恢复 V4.22/V4.24 的混合估值 'doRebalance' 逻辑
 * (这修复了 V4.25 中 "不交易" 的 Bug)
 */
public class RetailTrader extends BaseTrader {

    private class StockScore {
        Stock stock;
        double score;
        StockScore(Stock stock, double score) {
            this.stock = stock;
            this.score = score;
        }
    }

    // V4.25 IPO 参数
    private final double ipoHotSectorPercent;
    private final double ipoNormalPercent;

    // 【【V4.26 恢复】】 V4.22/V4.24 估值参数
    private final double fundamentalWeight;
    private final double trendWeight;
    private final double noiseStdDev;
    private final int lookbackDays;

    private static final double REBALANCE_TOLERANCE = 0.10;
    private static final double TARGET_INVESTMENT_RATIO = 0.95;
    private static final double ORDER_PRICE_BUFFER = 0.05;


    public RetailTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "RETAIL", initialCash, riskTolerance, maxStocks,
                Config.AGENT_RETAIL_MAX_STOCKS_MIN,
                Config.AGENT_RETAIL_MAX_STOCKS_MAX,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS);

        // V4.25 IPO
        this.ipoHotSectorPercent = Config.AGENT_RETAIL_IPO_HOT_SECTOR_PERCENT;
        this.ipoNormalPercent = Config.AGENT_RETAIL_IPO_NORMAL_PERCENT;

        // 【【V4.26 恢复】】
        this.fundamentalWeight = Config.AGENT_RETAIL_VALUATION_FUNDAMENTAL_WEIGHT;
        this.trendWeight = Config.AGENT_RETAIL_VALUATION_TREND_WEIGHT;
        this.noiseStdDev = Config.AGENT_RETAIL_VALUATION_NOISE_STDDEV;
        this.lookbackDays = Config.AGENT_RETAIL_VALUATION_LOOKBACK_DAYS;
    }

    // (V4.25 IPO 逻辑 - 保持不变)
    @Override
    public double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model) {
        double capitalPercentToAllocate = ipoNormalPercent;
        if (stock.sector == Sector.TECH || stock.sector == Sector.HEALTHCARE) {
            capitalPercentToAllocate = ipoHotSectorPercent;
        }
        return (this.portfolio.cash * capitalPercentToAllocate) / stock.ipoPrice;
    }

    /**
     * 【【V4.26 恢复】】 (V4.22/V4.24 逻辑)
     */
    @Override
    public void step(SimState state) {
        StockMarketSim model = (StockMarketSim) state;
        long currentStep = state.schedule.getSteps();
        if (currentStep < super.nextTradeStep) return;
        if (!model.market.isTradingHours()) return;
        if (currentStep % model.market.STEPS_PER_DAY != 1) return;
        doRebalance(model);
        super.setNextTradeStep(model);
    }

    /**
     * 【【V4.26 恢复】】 (V4.22/V4.24 混合估值)
     */
    private void doRebalance(StockMarketSim model) {
        // 1. 评估
        List<StockScore> allStockScores = new ArrayList<>();
        Bag allStocks = model.stocks;
        for (int i = 0; i < allStocks.size(); i++) {
            Stock stock = (Stock) allStocks.get(i);
            if (stock.currentPrice <= 0) continue;
            double fundamentalValue = model.valuation.calculateFundamentalValue(stock);
            double trendValue = model.market.getPriceTrend(stock, this.lookbackDays);
            double combinedValue = (fundamentalValue * this.fundamentalWeight) + (trendValue * this.trendWeight);
            double noise = 1.0 + (model.random.nextGaussian() * this.noiseStdDev);
            double finalValue = combinedValue * noise;
            double score = finalValue / stock.currentPrice;
            allStockScores.add(new StockScore(stock, score));
        }

        // 2. 选股
        Collections.sort(allStockScores, Comparator.comparingDouble((StockScore o) -> o.score).reversed());
        Set<Stock> targetPortfolio = new HashSet<>();
        for (int i = 0; i < this.maxStocks && i < allStockScores.size(); i++) {
            targetPortfolio.add(allStockScores.get(i).stock);
        }
        if (targetPortfolio.isEmpty()) return;

        // 3. 分配
        double totalAssets = portfolio.getTotalAssets();
        double targetPortfolioValue = totalAssets * TARGET_INVESTMENT_RATIO;
        double targetValuePerStock = (targetPortfolio.size() > 0) ? (targetPortfolioValue / targetPortfolio.size()) : 0;
        if (targetValuePerStock == 0) return;

        // 4. 执行 (卖出)
        Set<Stock> heldStocks = new HashSet<>(portfolio.getPositions().keySet());
        for (Stock heldStock : heldStocks) {
            double currentPrice = heldStock.currentPrice;
            if (currentPrice <= 0) continue;
            double currentValue = portfolio.getStockQuantity(heldStock) * currentPrice;
            double qtyToSell = 0;
            if (!targetPortfolio.contains(heldStock)) {
                qtyToSell = portfolio.getAvailableQuantity(heldStock);
            }
            else {
                double toleranceHigh = targetValuePerStock * (1.0 + REBALANCE_TOLERANCE);
                if (currentValue > toleranceHigh) {
                    double excessValue = currentValue - targetValuePerStock;
                    qtyToSell = excessValue / currentPrice;
                }
            }
            if (qtyToSell > 0) {
                qtyToSell = Math.min(qtyToSell, portfolio.getAvailableQuantity(heldStock));
                qtyToSell = Math.floor(qtyToSell / 100) * 100;
                if (qtyToSell > 0) {
                    model.market.submitSellOrder(this, heldStock, qtyToSell, currentPrice * (1.0 - ORDER_PRICE_BUFFER));
                }
            }
        }

        // 5. 执行 (买入)
        for (Stock targetStock : targetPortfolio) {
            double currentPrice = targetStock.currentPrice;
            if (currentPrice <= 0) continue;
            double currentValue = portfolio.getStockQuantity(targetStock) * currentPrice;
            double qtyToBuy = 0;
            boolean isHeld = portfolio.getPositions().containsKey(targetStock);
            if (!isHeld && !canBuyNewStock()) {
                continue;
            }
            double toleranceLow = targetValuePerStock * (1.0 - REBALANCE_TOLERANCE);
            if (currentValue < toleranceLow) {
                double valueToBuy = targetValuePerStock - currentValue;
                qtyToBuy = valueToBuy / currentPrice;
            }
            if (qtyToBuy > 0) {
                qtyToBuy = Math.floor(qtyToBuy / 100) * 100;
                double limitPrice = currentPrice * (1.0 + ORDER_PRICE_BUFFER);
                double cost = qtyToBuy * limitPrice;
                if (qtyToBuy > 0 && portfolio.reserveCash(cost)) {
                    model.market.submitBuyOrder(this, targetStock, qtyToBuy, limitPrice);
                }
            }
        }
    }

    @Override protected Stock chooseStock(StockMarketSim model) { return null; }
    @Override protected void makeDecision(StockMarketSim model, Stock stock) {}
}