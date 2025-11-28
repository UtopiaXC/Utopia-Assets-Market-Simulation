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

public class InstitutionalTrader extends BaseTrader {

    private class StockScore {
        Stock stock;
        double score; // Score > 1.0 表示被低估 (Buy/Hold), Score < 1.0 表示被高估 (Sell)
        StockScore(Stock stock, double score) {
            this.stock = stock;
            this.score = score;
        }
    }

    private static final double ORDER_PRICE_BUFFER = 0.02; // 缩小报价激进程度，防止自我踩踏

    // 估值参数
    private final double fundamentalWeight;
    private final double trendWeight;
    private final double noiseStdDev;
    private final int lookbackDays;

    public InstitutionalTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "INSTITUTIONAL", initialCash, riskTolerance, maxStocks,
                Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN,
                Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX,
                Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MAX_DAYS);

        this.fundamentalWeight = Config.AGENT_INSTITUTIONAL_VALUATION_FUNDAMENTAL_WEIGHT;
        this.trendWeight = Config.AGENT_INSTITUTIONAL_VALUATION_TREND_WEIGHT;
        this.noiseStdDev = Config.AGENT_INSTITUTIONAL_VALUATION_NOISE_STDDEV;
        this.lookbackDays = Config.AGENT_INSTITUTIONAL_VALUATION_LOOKBACK_DAYS;
    }

    @Override
    public double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model) {
        // (此方法在 V4.30 强制分配模式下不再重要，但保留逻辑)
        return 0;
    }

    @Override
    public void step(SimState state) {
        StockMarketSim model = (StockMarketSim) state;
        long currentStep = state.schedule.getSteps();
        if (currentStep < super.nextTradeStep) return;
        if (!model.market.isTradingHours()) return;
        // 机构通常每天做一次决策，或者更频繁
        doRebalance(model);
        super.setNextTradeStep(model);
    }

    /**
     * 【重写 V4.32】 稳健的再平衡策略
     * 防止因为"非 Top N"而强制抛售优质资产。
     */
    private void doRebalance(StockMarketSim model) {
        // 1. 对全市场股票打分
        List<StockScore> allStockScores = new ArrayList<>();
        Bag allStocks = model.stocks;

        for (int i = 0; i < allStocks.size(); i++) {
            Stock stock = (Stock) allStocks.get(i);
            if (stock.currentPrice <= 0) continue;

            double fundamentalValue = model.valuation.calculateFundamentalValue(stock);
            double trendValue = model.market.getPriceTrend(stock, this.lookbackDays);

            // 混合估值: 80% 基本面 + 20% 趋势
            double combinedValue = (fundamentalValue * this.fundamentalWeight) + (trendValue * this.trendWeight);

            // 加入个体噪音
            double noise = 1.0 + (model.random.nextGaussian() * this.noiseStdDev);
            double finalValue = combinedValue * noise;

            // Score = 估值 / 现价。 Score > 1 代表值得买。
            double score = finalValue / stock.currentPrice;
            allStockScores.add(new StockScore(stock, score));
        }

        // 2. 排序 (分数高在前)
        Collections.sort(allStockScores, Comparator.comparingDouble((StockScore o) -> o.score).reversed());

        // 3. 决策：卖出逻辑 (Sell Logic)
        // 只有当股票被高估 (Score < 1.0) 或者 严重不及预期 时才卖出
        // 不再强制因为数量限制而卖出盈利的股票

        Set<Stock> heldStocks = new HashSet<>(portfolio.getPositions().keySet());
        for (Stock heldStock : heldStocks) {
            double currentPrice = heldStock.currentPrice;
            double availableQty = portfolio.getAvailableQuantity(heldStock);
            if (availableQty <= 0) continue;

            // 找到这只股票现在的分数
            double myScore = 0;
            for(StockScore ss : allStockScores) {
                if(ss.stock == heldStock) { myScore = ss.score; break; }
            }

            // 【关键修改】
            // 如果 Score < 0.95 (高估 5% 以上)，坚决卖出。
            // 如果 Score > 1.05 (低估)，坚决持有 (HODL)。
            // 介于两者之间，观望。

            if (myScore < 0.95) {
                // 卖出全部
                model.market.submitSellOrder(this, heldStock, availableQty, currentPrice * (1.0 - ORDER_PRICE_BUFFER));
            }
            // 如果分数还不错 (myScore >= 0.95)，我们就不卖了！
            // 除非持仓数量真的超过了硬性上限 (Config.MAX_STOCKS) 太多，但我们已经放宽了 Config，所以这里不需要激进卖出。
        }

        // 4. 决策：买入逻辑 (Buy Logic)
        // 只买前 5 名，且必须是低估的 (Score > 1.05)
        int buyCount = 0;
        for (StockScore candidate : allStockScores) {
            if (buyCount >= 5) break; // 每次只关注头部几只

            // 只有当明显低估时才买
            if (candidate.score < 1.05) break; // 排序过的，后面都不行

            Stock targetStock = candidate.stock;
            // 如果已经持仓且仓位很重，就不加仓了 (简单的风控)
            // 这里简化：只有当现金充足时才买

            double currentPrice = targetStock.currentPrice;
            // 打算用总现金的 10% 去买一只
            double investAmount = portfolio.cash * 0.10;

            if (investAmount < currentPrice * 100) continue; // 钱不够买一手

            double qtyToBuy = Math.floor(investAmount / currentPrice / 100) * 100;
            if (qtyToBuy > 0 && canBuyNewStock()) {
                double limitPrice = currentPrice * (1.0 + ORDER_PRICE_BUFFER);
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