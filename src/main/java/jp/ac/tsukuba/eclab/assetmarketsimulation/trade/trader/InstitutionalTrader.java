package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import java.util.*;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.SocialNetwork;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import sim.engine.SimState;
import sim.util.Bag;

/**
 * 机构交易者
 * - 基本面权重高 (80%)
 * - 不使用杠杆 (作为配资提供方)
 * - 提供流动性
 */
public class InstitutionalTrader extends BaseTrader {

    private static final double ORDER_PRICE_BUFFER = 0.02;

    // 估值参数
    private final double wFundBase;
    private final double wSocialBase;
    private final double wTrendBase;
    private final double noiseStdDev;
    private final int lookbackDays;

    public InstitutionalTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "INSTITUTIONAL", initialCash, riskTolerance, maxStocks,
                Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN,
                Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX,
                Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MAX_DAYS);

        this.wFundBase = Config.VALUATION_FUND_WEIGHT_INST;
        this.wSocialBase = Config.VALUATION_SOCIAL_WEIGHT_INST;
        this.wTrendBase = Config.VALUATION_TREND_WEIGHT_INST;
        this.noiseStdDev = Config.VALUATION_NOISE_STDDEV_INST;
        this.lookbackDays = Config.VALUATION_LOOKBACK_DAYS_INST;
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
     * 再平衡策略 (使用三因子估值模型)
     */
    private void doRebalance(StockMarketSim model) {
        Bag allStocks = model.stocks;
        ValuationService valuation = model.valuation;
        SocialNetwork socialNet = model.socialNetwork;

        // 获取社交网络邻居
        List<BaseTrader> neighbors = (socialNet != null) ? socialNet.getNeighbors(this) : Collections.emptyList();
        double[] similarities = (socialNet != null) ? socialNet.getSimilarities(this) : new double[0];

        // 对全市场股票打分
        List<StockScore> allStockScores = new ArrayList<>();

        for (int i = 0; i < allStocks.size(); i++) {
            Stock stock = (Stock) allStocks.get(i);
            if (stock.currentPrice <= 0) continue;

            double noise = model.random.nextGaussian();
            ValuationService.BeliefResult result = valuation.calculateBelief(
                    this, stock, model.market,
                    neighbors, similarities,
                    wFundBase, wSocialBase, wTrendBase,
                    noiseStdDev, lookbackDays, noise);

            // 更新信念
            setBelief(stock, result.belief);            // 生成最新社交影响 JSON
            StringBuilder json = new StringBuilder("[");
            double simSum = 0;
            for (double s : similarities) simSum += s;
            for (int j = 0; j < neighbors.size(); j++) {
                BaseTrader n = neighbors.get(j);
                double sim = j < similarities.length ? similarities[j] : 0;
                double weight = (simSum > 0) ? (sim / simSum) : 0;
                json.append(String.format(
                        Locale.US,
                        "{\"neighborId\":%d,\"similarity\":%.4f,\"weight\":%.4f,\"belief\":%.4f}%s",
                        n.traderId, sim, weight, n.getBelief(stock),
                        (j < neighbors.size() - 1) ? "," : ""
                ));
            }
            json.append("]");
            setLastInfluenceJson(json.toString());

            // Score = 估值 / 现价
            double score = result.belief / stock.currentPrice;
            allStockScores.add(new StockScore(stock, score));
        }

        Collections.sort(allStockScores, Comparator.comparingDouble((StockScore o) -> o.score).reversed());

        // 卖出逻辑: 高估 (Score < 0.95) 才卖
        Set<Stock> heldStocks = new HashSet<>(portfolio.getPositions().keySet());
        for (Stock heldStock : heldStocks) {
            double availableQty = portfolio.getAvailableQuantity(heldStock);
            if (availableQty <= 0) continue;

            double myScore = 0;
            for (StockScore ss : allStockScores) {
                if (ss.stock == heldStock) { myScore = ss.score; break; }
            }

            if (myScore < 0.95) {
                model.market.submitSellOrder(this, heldStock, availableQty,
                        heldStock.currentPrice * (1.0 - ORDER_PRICE_BUFFER));
            }
        }

        // 买入逻辑: 只买前 5 名, 且必须低估 (Score > 1.05)
        int buyCount = 0;
        for (StockScore candidate : allStockScores) {
            if (buyCount >= 5) break;
            if (candidate.score < 1.05) break;

            Stock targetStock = candidate.stock;
            double investAmount = portfolio.cash * 0.10;
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

    private static class StockScore {
        Stock stock;
        double score;
        StockScore(Stock stock, double score) {
            this.stock = stock;
            this.score = score;
        }
    }
}