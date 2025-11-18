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

    // 【【V4.25 恢复】】
    @Override
    public double calculateIPOSubscription(Stock stock, ValuationService valuation, StockMarketSim model) {
        // (使用 V4.25 Config 中 10% 的高百分比)
        double percentRange = ipoMaxPercent - ipoMinPercent;
        double capitalPercentToAllocate = ipoMinPercent + (percentRange * model.random.nextDouble());
        return (this.portfolio.cash * capitalPercentToAllocate) / stock.ipoPrice;
    }

    // (V4.18 逻辑 - 保持不变)
    @Override
    protected Stock chooseStock(StockMarketSim model) {
        if (model.stocks.isEmpty()) return null;
        return (Stock) model.stocks.get(model.random.nextInt(model.stocks.size()));
    }

    // (V4.18 逻辑 - 保持不变)
    @Override
    protected void makeDecision(StockMarketSim model, Stock stock) {
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
}