package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;

/**
 * 政策插槽：运行时可动态修改的市场监管政策参数
 * Policy Slot: Runtime-mutable market regulation parameters
 *
 * P = [L_limit, Lev_max, Th_halt, N_settle]
 *
 * - Price Limits (L_limit): 单日涨跌幅限制
 * - Circuit Breakers (Th_halt): 市场整体熔断百分比
 * - Leverage Restrictions (Lev_max): 最大杠杆倍率
 * - Settlement Limits (N_settle): T+N 交易结算限制
 */
public class PolicySlot {

    // Price Limits: 单日涨跌幅限制 (e.g., 0.10 = ±10%)
    private volatile double priceLimitRatio;

    // Circuit Breakers: 市场指数跌幅触发熔断阈值 (e.g., 0.07 = 7%)
    private volatile double circuitBreakerThreshold;

    // Leverage Restrictions: 最大杠杆倍率 (e.g., 2.0 = 2x)
    private volatile double maxLeverageRatio;

    // Settlement Limits: T+N 天数 (e.g., 1 = T+1)
    private volatile int settlementDays;

    public PolicySlot() {
        this.priceLimitRatio = Config.POLICY_PRICE_LIMIT_RATIO;
        this.circuitBreakerThreshold = Config.POLICY_CIRCUIT_BREAKER_THRESHOLD;
        this.maxLeverageRatio = Config.POLICY_MAX_LEVERAGE_RATIO;
        this.settlementDays = Config.POLICY_SETTLEMENT_DAYS;
    }

    public PolicySlot(double priceLimitRatio, double circuitBreakerThreshold,
                      double maxLeverageRatio, int settlementDays) {
        this.priceLimitRatio = priceLimitRatio;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.maxLeverageRatio = maxLeverageRatio;
        this.settlementDays = settlementDays;
    }

    // Getters & Setters

    public double getPriceLimitRatio() {
        return priceLimitRatio;
    }

    public void setPriceLimitRatio(double priceLimitRatio) {
        this.priceLimitRatio = priceLimitRatio;
    }

    public double getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public void setCircuitBreakerThreshold(double circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public double getMaxLeverageRatio() {
        return maxLeverageRatio;
    }

    public void setMaxLeverageRatio(double maxLeverageRatio) {
        this.maxLeverageRatio = maxLeverageRatio;
    }

    public int getSettlementDays() {
        return settlementDays;
    }

    public void setSettlementDays(int settlementDays) {
        this.settlementDays = settlementDays;
    }

    @Override
    public String toString() {
        return String.format("PolicySlot[L_limit=%.2f, Th_halt=%.2f, Lev_max=%.1f, N_settle=T+%d]",
                priceLimitRatio, circuitBreakerThreshold, maxLeverageRatio, settlementDays);
    }
}
