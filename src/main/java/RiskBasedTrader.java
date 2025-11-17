import sim.engine.SimState;
import sim.engine.Steppable;

public class RiskBasedTrader implements Steppable {

    public final int traderId;
    public Portfolio portfolio;

    public double riskTolerance;
    public double tradingFrequency;

    private double basePBRatio;
    private double mutationRate = 0.01;
    private double mutationStdDev = 0.05;

    public RiskBasedTrader(int id, double initialCash, double riskTolerance, double tradingFrequency) {
        this.traderId = id;
        this.portfolio = new Portfolio(initialCash);
        this.riskTolerance = riskTolerance;

        // 【修改 1/2】提高基础交易频率 (让市场更活跃)
        // (旧: 0.1 + 0.5*... -> 10%~60%)
        // (新: 0.3 + 0.4*... -> 30%~70%)
        this.tradingFrequency = 0.3 + (0.4 * tradingFrequency);

        updateBasePBRatio();
    }

    private void updateBasePBRatio() {
        this.basePBRatio = 1.0 + this.riskTolerance * 9.0;
    }

    public void mutateTraits(SimState state) {
        if (state.random.nextDouble() < mutationRate) {
            this.riskTolerance += state.random.nextGaussian() * mutationStdDev;
            if (this.riskTolerance > 1.0) this.riskTolerance = 1.0;
            if (this.riskTolerance < 0.0) this.riskTolerance = 0.0;
            updateBasePBRatio();
        }

        if (state.random.nextDouble() < mutationRate) {
            this.tradingFrequency += state.random.nextGaussian() * mutationStdDev;
            // 保持在 [0.3, 0.7] 范围内
            if (this.tradingFrequency > 0.7) this.tradingFrequency = 0.7;
            if (this.tradingFrequency < 0.3) this.tradingFrequency = 0.3;
        }
    }

    @Override
    public void step(SimState state) {
        StockMarketSim model = (StockMarketSim) state;

        if (!model.market.isTradingHours()) {
            return;
        }

        if (model.random.nextDouble() > this.tradingFrequency) {
            return;
        }

        if (model.stocks.isEmpty()) return;
        Stock stock = (Stock) model.stocks.get(model.random.nextInt(model.stocks.size()));

        // 【修改 2/2】增加估值噪音 (让市场更活跃)
        // (旧: * 0.1)
        double noise = 1.0 + model.random.nextGaussian() * 0.2;
        double dynamicPBRatio = this.basePBRatio * noise;
        if (dynamicPBRatio < 0.1) dynamicPBRatio = 0.1;

        double perceivedValue = stock.netAssetsPerShare * dynamicPBRatio;

        double currentPrice = stock.currentPrice;

        // 【规则】交易单位为 100 股 (1手)
        double orderQuantity = 100;

        if (currentPrice < perceivedValue) {
            if (portfolio.cash >= currentPrice * orderQuantity) {
                model.market.submitBuyOrder(this, stock, orderQuantity);
            }
        } else if (currentPrice > perceivedValue) {
            if (portfolio.getStockQuantity(stock) >= orderQuantity) {
                model.market.submitSellOrder(this, stock, orderQuantity);
            }
        }
    }
}