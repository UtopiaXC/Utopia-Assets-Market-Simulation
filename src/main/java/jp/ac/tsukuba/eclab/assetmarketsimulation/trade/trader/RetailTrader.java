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
 * 普通散户交易者
 * - 基本面与趋势各半 (50/25/25)
 * - 可以使用杠杆 (配资)
 * - 有保本/止盈/破产逻辑
 */
public class RetailTrader extends BaseTrader {

    private static final double ORDER_PRICE_BUFFER = 0.02;

    private final double wFundBase;
    private final double wSocialBase;
    private final double wTrendBase;
    private final double noiseStdDev;
    private final int lookbackDays;

    // 是否已经完成保本操作
    private boolean hasSecuredPrincipal = false;

    public RetailTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "RETAIL", initialCash, riskTolerance, maxStocks,
                Config.AGENT_RETAIL_MAX_STOCKS_MIN,
                Config.AGENT_RETAIL_MAX_STOCKS_MAX,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS);

        this.wFundBase = Config.VALUATION_FUND_WEIGHT_RETAIL;
        this.wSocialBase = Config.VALUATION_SOCIAL_WEIGHT_RETAIL;
        this.wTrendBase = Config.VALUATION_TREND_WEIGHT_RETAIL;
        this.noiseStdDev = Config.VALUATION_NOISE_STDDEV_RETAIL;
        this.lookbackDays = Config.VALUATION_LOOKBACK_DAYS_RETAIL;
    }

    @Override
    public double checkWithdrawal() {
        if (!this.isActive()) return 0;
        double withdrawAmount = 0;

        if (!hasSecuredPrincipal) {
            if (portfolio.cash > initialCapitalRecorded * Config.AGENT_RETAIL_PRINCIPAL_SECURE_BUFFER) {
                withdrawAmount = initialCapitalRecorded;
                hasSecuredPrincipal = true;
            }
        } else {
            double totalStockValue = portfolio.getTotalStockValue();
            double totalAssets = portfolio.cash + totalStockValue;
            if (portfolio.cash > totalAssets * Config.AGENT_RETAIL_PROFIT_SKIM_TRIGGER && portfolio.cash > 10000) {
                withdrawAmount = portfolio.cash * Config.AGENT_RETAIL_PROFIT_SKIM_RATIO;
            }
        }
        return withdrawAmount;
    }

    @Override
    public boolean isBankrupt() {
        if (!this.isActive()) return false;
        if (!hasSecuredPrincipal) {
            double totalAssets = portfolio.getTotalAssets();
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

        // 有概率请求配资
        if (model.random.nextDouble() < Config.LEVERAGE_REQUEST_PROB && portfolio.borrowedCash <= 0) {
            double maxLev = model.market.policySlot.getMaxLeverageRatio();
            if (maxLev > 1.0 && model.leverageService != null) {
                model.leverageService.requestLeverage(this, maxLev);
            }
        }

        doRebalance(model);
        super.setNextTradeStep(model);
    }

    private void doRebalance(StockMarketSim model) {
        Bag allStocks = model.stocks;
        ValuationService valuation = model.valuation;
        SocialNetwork socialNet = model.socialNetwork;

        List<BaseTrader> neighbors = (socialNet != null) ? socialNet.getNeighbors(this) : Collections.emptyList();
        double[] similarities = (socialNet != null) ? socialNet.getSimilarities(this) : new double[0];

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

            double score = result.belief / stock.currentPrice;
            allStockScores.add(new StockScore(stock, score));
        }

        Collections.sort(allStockScores, Comparator.comparingDouble((StockScore o) -> o.score).reversed());

        // 卖出逻辑
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

        // 买入逻辑
        int buyCount = 0;
        for (StockScore candidate : allStockScores) {
            if (buyCount >= 2) break;
            if (candidate.score < 1.05) break;

            Stock targetStock = candidate.stock;
            double investAmount = portfolio.cash * 0.20;
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