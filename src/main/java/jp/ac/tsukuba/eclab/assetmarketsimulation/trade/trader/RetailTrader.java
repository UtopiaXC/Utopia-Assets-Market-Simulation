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

    @Override
    public void step(SimState state) {
        StockMarketSim model = (StockMarketSim) state;
        long currentStep = state.schedule.getSteps();
        if (currentStep < super.nextTradeStep) return;
        if (!model.market.isTradingHours()) return;
        doRebalance(model);
        super.setNextTradeStep(model);
    }

    /**
     * 【重写 V4.32】 散户逻辑：追涨杀跌，但也看估值
     */
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