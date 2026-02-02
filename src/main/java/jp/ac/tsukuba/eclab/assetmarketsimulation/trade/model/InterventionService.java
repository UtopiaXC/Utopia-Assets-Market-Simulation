package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import sim.util.Bag;

/**
 * 上帝之手：用于施加外部干预（新闻、政策、流动性）
 */
public class InterventionService {

    private StockMarketSim sim;

    public InterventionService(StockMarketSim sim) {
        this.sim = sim;
    }

    // ==========================================
    // 1. 流动性干预 (Liquidity Shocks)
    // ==========================================

    /**
     * 量化宽松 (QE) / 撒钱
     * 
     * @param amountPerAgent 每个 Agent 获得的现金数额
     */
    public void injectLiquidity(double amountPerAgent) {
        System.out.println(">>> [INTERVENTION] Liquidity Injection: +" + amountPerAgent + " per agent.");
        Bag traders = sim.traders;
        for (int i = 0; i < traders.size(); i++) {
            BaseTrader trader = (BaseTrader) traders.get(i);
            trader.portfolio.cash += amountPerAgent;
        }
    }

    /**
     * 流动性收紧 / 加息
     * 
     * @param percent    抽走现金的百分比 (例如 0.1 表示抽走 10% 现金)
     * @param riskImpact 对风险偏好的打击 (例如 0.2 表示风险容忍度降低 0.2)
     */
    public void tightenLiquidity(double percent, double riskImpact) {
        System.out.println(">>> [INTERVENTION] Liquidity Tightening: Cash -" + (percent * 100) + "%, Risk Tolerance -"
                + riskImpact);
        Bag traders = sim.traders;
        for (int i = 0; i < traders.size(); i++) {
            BaseTrader trader = (BaseTrader) traders.get(i);
            // 1. 抽水
            if (trader.portfolio.cash > 0) {
                trader.portfolio.cash *= (1.0 - percent);
            }
            // 2. 降低风险偏好 (加息导致避险)
            trader.riskTolerance -= riskImpact;
            if (trader.riskTolerance < 0.05)
                trader.riskTolerance = 0.05;
        }
    }

    /**
     * 调整全局风险容忍度
     * 
     * @param delta 风险容忍度变化值 (正数提升，负数降低)
     */
    public void adjustRiskTolerance(double delta) {
        System.out.println(">>> [INTERVENTION] Risk Tolerance Adjustment: " + (delta > 0 ? "+" : "") + delta);
        Bag traders = sim.traders;
        for (int i = 0; i < traders.size(); i++) {
            BaseTrader trader = (BaseTrader) traders.get(i);
            trader.riskTolerance += delta;
            // 限制在合理范围
            if (trader.riskTolerance < 0.05)
                trader.riskTolerance = 0.05;
            if (trader.riskTolerance > 1.0)
                trader.riskTolerance = 1.0;
        }
    }

    // ==========================================
    // 2. 行业基本面干预 (Fundamental Shocks)
    // ==========================================

    /**
     * 行业基本面冲击 (真实盈利变化)
     * 
     * @param sector           目标板块
     * @param epsChangePercent EPS 变化幅度 (例如 0.5 表示盈利增加 50%, -0.2 表示减少 20%)
     */
    public void triggerSectorFundamentalShock(Sector sector, double epsChangePercent) {
        System.out.println(">>> [INTERVENTION] Fundamental Shock on " + sector + ": EPS "
                + (epsChangePercent > 0 ? "+" : "") + (epsChangePercent * 100) + "%");
        Bag stocks = sim.stocks;
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = (Stock) stocks.get(i);
            if (stock.sector == sector) {
                // 直接修改 EPS 和季度 EPS
                stock.eps *= (1.0 + epsChangePercent);
                stock.latestQuarterlyEps *= (1.0 + epsChangePercent);
                // 股价不会立即变，但 Agent 计算估值时会发现它变了
            }
        }
    }

    // ==========================================
    // 3. 市场情绪/估值干预 (Sentiment Shocks)
    // ==========================================

    /**
     * 行业情绪冲击 (估值倍数变化)
     * 比如：AI 概念火热，科技股本身不赚钱，但大家愿意给 100倍 PE。
     * 
     * @param sector              目标板块
     * @param sentimentMultiplier 情绪乘数 (例如 1.5 表示该板块估值溢价 50%)
     */
    public void triggerSectorSentimentShock(Sector sector, double sentimentMultiplier) {
        System.out.println(">>> [INTERVENTION] Sentiment Boom on " + sector + ": Valuation x" + sentimentMultiplier);
        // 调用 ValuationService 更新情绪表
        sim.valuation.setSectorSentiment(sector, sentimentMultiplier);
    }

    /**
     * 重置所有情绪
     */
    public void resetSentiment() {
        System.out.println(">>> [INTERVENTION] Sentiment Reset.");
        sim.valuation.resetSectorSentiment();
    }
}