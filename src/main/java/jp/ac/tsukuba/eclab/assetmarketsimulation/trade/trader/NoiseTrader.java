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
 * 噪声交易者
 * - 趋势 + 社交主导 (10/30/60)
 * - 可以使用杠杆 (配资)
 * - 行为更随机
 */
public class NoiseTrader extends BaseTrader {

    private static final double ORDER_PRICE_BUFFER = 0.03; // 噪声更大

    private final double wFundBase;
    private final double wSocialBase;
    private final double wTrendBase;
    private final double noiseStdDev;
    private final int lookbackDays;

    private boolean hasSecuredPrincipal = false;

    public NoiseTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "NOISE", initialCash, riskTolerance, maxStocks,
                Config.AGENT_NOISE_MAX_STOCKS_MIN,
                Config.AGENT_NOISE_MAX_STOCKS_MAX,
                Config.AGENT_NOISE_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_NOISE_TRADE_INTERVAL_MAX_DAYS);

        this.wFundBase = Config.VALUATION_FUND_WEIGHT_NOISE;
        this.wSocialBase = Config.VALUATION_SOCIAL_WEIGHT_NOISE;
        this.wTrendBase = Config.VALUATION_TREND_WEIGHT_NOISE;
        this.noiseStdDev = Config.VALUATION_NOISE_STDDEV_NOISE;
        this.lookbackDays = Config.VALUATION_LOOKBACK_DAYS_NOISE;

        // 噪声交易者社交敏感度更高
        this.socialSensitivity = Config.SOCIAL_SENSITIVITY_BETA * 1.5;
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

        // 噪声交易者更容易申请配资
        if (model.random.nextDouble() < Config.LEVERAGE_REQUEST_PROB * 2 && portfolio.borrowedCash <= 0) {
            double maxLev = model.market.policySlot.getMaxLeverageRatio();
            if (maxLev > 1.0 && model.leverageService != null) {
                model.leverageService.requestLeverage(this, maxLev);
            }
        }

        // 有时直接随机交易, 有时使用估值模型
        if (model.random.nextDouble() < 0.3) {
            doRandomTrade(model);
        } else {
            doBeliefBasedTrade(model);
        }
        super.setNextTradeStep(model);
    }

    /**
     * 纯随机交易 (30% 概率)
     */
    private void doRandomTrade(StockMarketSim model) {
        if (model.stocks.isEmpty()) return;
        Stock stock = (Stock) model.stocks.get(model.random.nextInt(model.stocks.size()));

        double orderQuantity = 100;
        double noise = 1.0 + (model.random.nextGaussian() * noiseStdDev);
        double limitPrice = stock.currentPrice * noise;

        if (model.random.nextBoolean()) {
            double cost = limitPrice * orderQuantity;
            if (portfolio.reserveCash(cost)) {
                model.market.submitBuyOrder(this, stock, orderQuantity, limitPrice);
            }
        } else {
            if (portfolio.getAvailableQuantity(stock) >= orderQuantity) {
                model.market.submitSellOrder(this, stock, orderQuantity, limitPrice);
            }
        }
    }

    /**
     * 基于信念的交易 (70% 概率)
     */
    private void doBeliefBasedTrade(StockMarketSim model) {
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

        // 卖出
        Set<Stock> heldStocks = new HashSet<>(portfolio.getPositions().keySet());
        for (Stock heldStock : heldStocks) {
            double availableQty = portfolio.getAvailableQuantity(heldStock);
            if (availableQty <= 0) continue;

            double myScore = 0;
            for (StockScore ss : allStockScores) {
                if (ss.stock == heldStock) { myScore = ss.score; break; }
            }

            if (myScore < 0.90) { // 噪声交易者更容易恐慌卖出
                model.market.submitSellOrder(this, heldStock, availableQty,
                        heldStock.currentPrice * (1.0 - ORDER_PRICE_BUFFER));
            }
        }

        // 买入 (只买 1 只)
        for (StockScore candidate : allStockScores) {
            if (candidate.score < 1.03) break; // 阈值更低

            Stock targetStock = candidate.stock;
            double investAmount = portfolio.cash * 0.30;
            if (investAmount < targetStock.currentPrice * 100) continue;

            double qtyToBuy = Math.floor(investAmount / targetStock.currentPrice / 100) * 100;
            if (qtyToBuy > 0 && canBuyNewStock()) {
                double limitPrice = targetStock.currentPrice * (1.0 + ORDER_PRICE_BUFFER);
                if (portfolio.reserveCash(qtyToBuy * limitPrice)) {
                    model.market.submitBuyOrder(this, targetStock, qtyToBuy, limitPrice);
                    break;
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