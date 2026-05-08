package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.PolicySlot;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三因子估值模型 (Slides Page 7)
 *
 * 信念: φ_{A,t}^i = w_fund × V_fund + w_social × V_social + w_trend × V_trend + ε
 *
 * - V_fund: 基本面价值 (BPS, EPS, P/B, P/E)
 * - V_social: 社交网络传染 (邻居信念加权)
 * - V_trend: 趋势追踪 (价格动量)
 *
 * 权重动态调整:
 * - w'_social = w_social_base × (1 + β × σ / Th_halt)
 * - w'_trend = w_trend_base × (1 + β × |R_t^τ| / (ΔP + ε))
 * - w = w' / Σ(w'_n)  归一化
 */
public class ValuationService {

    private final double pbMultiplier;  // M_PB
    private final double peMultiplier;  // M_PE

    // 板块加成
    private final Map<Sector, Double> sectorBonus;

    public ValuationService() {
        this.pbMultiplier = Config.VALUATION_PB_MULTIPLIER;
        this.peMultiplier = Config.VALUATION_PE_MULTIPLIER;

        this.sectorBonus = new HashMap<>();
        this.sectorBonus.put(Sector.TECH, Config.VALUATION_SECTOR_TECH);
        this.sectorBonus.put(Sector.HEALTHCARE, Config.VALUATION_SECTOR_HEALTHCARE);
        this.sectorBonus.put(Sector.CONSUMER, Config.VALUATION_SECTOR_CONSUMER);
        this.sectorBonus.put(Sector.FINANCE, Config.VALUATION_SECTOR_FINANCE);
        this.sectorBonus.put(Sector.INDUSTRY, Config.VALUATION_SECTOR_INDUSTRY);
        this.sectorBonus.put(Sector.ENERGY, Config.VALUATION_SECTOR_ENERGY);
    }

    /**
     * 计算完整的信念值 φ_{A,t}^i
     *
     * @param agent        交易者
     * @param stock        目标股票
     * @param market       市场
     * @param neighbors    社交网络邻居列表
     * @param similarities 各邻居的相似度 (与 neighbors 对应)
     * @param wFundBase    基本面权重基础值
     * @param wSocialBase  社交权重基础值
     * @param wTrendBase   趋势权重基础值
     * @param noiseStdDev  噪声标准差
     * @param lookbackDays 趋势回顾天数
     * @param noise        噪声值 (由调用方传入, 来自 state.random)
     * @return BeliefResult 包含信念值和各分量细节
     */
    public BeliefResult calculateBelief(
            BaseTrader agent, Stock stock, Market market,
            List<BaseTrader> neighbors, double[] similarities,
            double wFundBase, double wSocialBase, double wTrendBase,
            double noiseStdDev, int lookbackDays, double noise) {

        PolicySlot policy = market.policySlot;

        // 1. 基本面估值 V_fund
        double vFund = calculateFundamentalValue(stock);

        // 2. 社交估值 V_social
        double vSocial = calculateSocialValue(stock, neighbors, similarities);

        // 3. 趋势估值 V_trend
        double priceReturn = market.getPriceReturn(stock, lookbackDays);
        double vTrend = stock.currentPrice * (1.0 + priceReturn);

        // 4. 动态权重调整
        double beta = agent.socialSensitivity;
        double sigma = market.getMarketVolatility(20); // 近20天波动率
        double thHalt = policy.getCircuitBreakerThreshold();
        double priceLimitRatio = policy.getPriceLimitRatio();
        double deltaP = stock.limitUp - stock.currentPrice;
        double epsilon = 0.001;

        // w'_fund = w_fund_base (基本面权重不变)
        double wFundPrime = wFundBase;

        // w'_social = w_social_base × (1 + β × σ / Th_halt)
        double socialBoost = (thHalt > 0) ? (beta * sigma / thHalt) : 0;
        double wSocialPrime = wSocialBase * (1.0 + socialBoost);

        // w'_trend = w_trend_base × (1 + β × |R_t^τ| / (ΔP + ε))
        double trendBoost = (deltaP + epsilon > 0) ? (beta * Math.abs(priceReturn) / (deltaP + epsilon)) : 0;
        double wTrendPrime = wTrendBase * (1.0 + trendBoost);

        // 归一化
        double wSum = wFundPrime + wSocialPrime + wTrendPrime;
        double wFund = (wSum > 0) ? wFundPrime / wSum : 1.0 / 3.0;
        double wSocial = (wSum > 0) ? wSocialPrime / wSum : 1.0 / 3.0;
        double wTrend = (wSum > 0) ? wTrendPrime / wSum : 1.0 / 3.0;

        // 5. 最终信念
        double belief = wFund * vFund + wSocial * vSocial + wTrend * vTrend;

        // 加入噪声
        belief *= (1.0 + noise * noiseStdDev);

        return new BeliefResult(belief, vFund, vSocial, vTrend, wFund, wSocial, wTrend);
    }

    /**
     * 基本面估值 V_fund (Slides Page 7)
     *
     * V_base = (BPS × M_PB + E × M_PE) / 2    (if E > 0)
     * V_base = BPS × M_PB                       (if E ≤ 0)
     * V_fund = V_base × (1 + g) × sector_bonus
     */
    public double calculateFundamentalValue(Stock stock) {
        double bps = stock.netAssetsPerShare;
        double eps = stock.eps;

        double pbValue = bps * pbMultiplier;
        double peValue = (eps > 0) ? eps * peMultiplier : 0;

        double baseValue = (peValue > 0) ? (pbValue + peValue) / 2.0 : pbValue;
        double growthMultiplier = 1.0 + stock.earningsGrowth;
        double sectorMult = sectorBonus.getOrDefault(stock.sector, 1.0);

        return baseValue * growthMultiplier * sectorMult;
    }

    /**
     * 社交估值 V_social (Slides Page 7-8)
     *
     * V_social = Σ_{j∈Neigh} (Sim(A,j) / Σ Sim) × φ_j
     */
    private double calculateSocialValue(Stock stock, List<BaseTrader> neighbors, double[] similarities) {
        if (neighbors == null || neighbors.isEmpty()) {
            return stock.currentPrice; // 没有邻居时返回当前价格
        }

        double simSum = 0;
        for (double sim : similarities) {
            simSum += sim;
        }
        if (simSum <= 0) return stock.currentPrice;

        double weightedBelief = 0;
        for (int i = 0; i < neighbors.size(); i++) {
            BaseTrader neighbor = neighbors.get(i);
            double neighborBelief = neighbor.getBelief(stock);
            double weight = similarities[i] / simSum;
            weightedBelief += weight * neighborBelief;
        }

        return weightedBelief;
    }

    /**
     * 计算结果封装
     */
    public static class BeliefResult {
        public final double belief;      // 最终信念值
        public final double vFund;       // 基本面分量
        public final double vSocial;     // 社交分量
        public final double vTrend;      // 趋势分量
        public final double wFund;       // 归一化后的基本面权重
        public final double wSocial;     // 归一化后的社交权重
        public final double wTrend;      // 归一化后的趋势权重

        public BeliefResult(double belief, double vFund, double vSocial, double vTrend,
                            double wFund, double wSocial, double wTrend) {
            this.belief = belief;
            this.vFund = vFund;
            this.vSocial = vSocial;
            this.vTrend = vTrend;
            this.wFund = wFund;
            this.wSocial = wSocial;
            this.wTrend = wTrend;
        }
    }
}