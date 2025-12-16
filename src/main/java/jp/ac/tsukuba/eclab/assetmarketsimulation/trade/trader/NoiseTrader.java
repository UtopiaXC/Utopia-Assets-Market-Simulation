package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import sim.engine.SimState;

public class NoiseTrader extends BaseTrader {

    private final double ipoMinPercent;
    private final double ipoMaxPercent;
    private final double noiseStdDev;

    // 【新增 V4.33】 是否已经完成保本操作 (与 Retail 保持一致)
    private boolean hasSecuredPrincipal = false;

    public NoiseTrader(int id, double initialCash, double riskTolerance, int maxStocks) {
        super(id, "NOISE", initialCash, riskTolerance, maxStocks,
                Config.AGENT_NOISE_MAX_STOCKS_MIN,
                Config.AGENT_NOISE_MAX_STOCKS_MAX,
                Config.AGENT_NOISE_TRADE_INTERVAL_MIN_DAYS,
                Config.AGENT_NOISE_TRADE_INTERVAL_MAX_DAYS);

        this.ipoMinPercent = Config.AGENT_NOISE_IPO_MIN_PERCENT;
        this.ipoMaxPercent = Config.AGENT_NOISE_IPO_MAX_PERCENT;
        this.noiseStdDev = Config.AGENT_NOISE_VALUATION_NOISE_STDDEV;
    }

    @Override
    public double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model) {
        double percentRange = ipoMaxPercent - ipoMinPercent;
        double capitalPercentToAllocate = ipoMinPercent + (percentRange * model.random.nextDouble());
        return (this.portfolio.cash * capitalPercentToAllocate) / stock.ipoPrice;
    }

    /**
     * 【新增 V4.33】 噪音交易者也是散户，共享同样的资金撤出逻辑
     */
    @Override
    public double checkWithdrawal() {
        if (!this.isActive()) return 0;

        double withdrawAmount = 0;

        // 1. 保本阶段
        if (!hasSecuredPrincipal) {
            if (portfolio.cash > initialCapitalRecorded * Config.AGENT_RETAIL_PRINCIPAL_SECURE_BUFFER) {
                withdrawAmount = initialCapitalRecorded;
                hasSecuredPrincipal = true;
            }
        }
        // 2. 利润收割阶段
        else {
            double totalStockValue = portfolio.getTotalStockValue();
            double totalAssets = portfolio.cash + totalStockValue;

            if (portfolio.cash > totalAssets * Config.AGENT_RETAIL_PROFIT_SKIM_TRIGGER && portfolio.cash > 10000) {
                withdrawAmount = portfolio.cash * Config.AGENT_RETAIL_PROFIT_SKIM_RATIO;
            }
        }
        return withdrawAmount;
    }

    /**
     * 【新增 V4.33】 噪音交易者共享同样的破产逻辑
     */
    @Override
    public boolean isBankrupt() {
        if (!this.isActive()) return false;

        if (!hasSecuredPrincipal) {
            double totalAssets = portfolio.getTotalAssets();
            // 使用散户的绝望阈值
            if (totalAssets < initialCapitalRecorded * Config.AGENT_RETAIL_DESPAIR_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Stock chooseStock(StockMarketSim model) {
        if (model.stocks.isEmpty()) return null;
        return (Stock) model.stocks.get(model.random.nextInt(model.stocks.size()));
    }

    @Override
    protected void makeDecision(StockMarketSim model, Stock stock) {
        double orderQuantity = 100;
        double noise = 1.0 + (model.random.nextGaussian() * noiseStdDev);
        double limitPrice = stock.currentPrice * noise;

        // 简单的随机交易逻辑保持不变
        if (model.random.nextBoolean()) {
            double cost = limitPrice * orderQuantity;
            // 只有钱够才买
            if (portfolio.reserveCash(cost)) {
                model.market.submitBuyOrder(this, stock, orderQuantity, limitPrice);
            }
        } else {
            if (portfolio.getAvailableQuantity(stock) >= orderQuantity) {
                model.market.submitSellOrder(this, stock, orderQuantity, limitPrice);
            }
        }
    }
}