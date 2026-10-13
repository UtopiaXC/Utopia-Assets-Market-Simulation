package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;

/**
 * 政策插槽：运行时可动态修改的市场监管与宏观政策参数
 * Policy Slot: Runtime-mutable market regulation & fiscal/monetary policy parameters
 *
 * P = [L_limit, Lev_max, Th_halt, N_settle, r_f, RRR, Tax_stamp, Tax_cgt, Tax_div, T_disclose, θ_censor]
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

    // 货币政策：基准年利率 (e.g., 0.03 = 3%)
    private volatile double benchmarkInterestRate;

    // 货币政策：存款准备金率 (e.g., 0.10 = 10%)
    private volatile double requiredReserveRatio;

    // 财税政策：印花税率 (e.g., 0.001 = 0.1% 卖方)
    private volatile double stampDutyRate;

    // 财税政策：资本利得税率 (e.g., 0.10 = 10% 仅对盈利平仓部分)
    private volatile double capitalGainsTaxRate;

    // 财税政策：股息红利税率 (e.g., 0.10 = 10% 分红时扣除)
    private volatile double dividendTaxRate;

    // 监管政策：信息披露/财报发布周期天数 (e.g., 60 天/季度)
    private volatile int earningsDisclosurePeriodDays;

    // 舆论管制：社交网络信息传播阻尼系数 [0, 1] (0 = 无管制, 1 = 完全阻断社交跟风)
    private volatile double opinionCensorshipDamping;

    public PolicySlot() {
        this.priceLimitRatio = Config.POLICY_PRICE_LIMIT_RATIO;
        this.circuitBreakerThreshold = Config.POLICY_CIRCUIT_BREAKER_THRESHOLD;
        this.maxLeverageRatio = Config.POLICY_MAX_LEVERAGE_RATIO;
        this.settlementDays = Config.POLICY_SETTLEMENT_DAYS;

        this.benchmarkInterestRate = Config.POLICY_BENCHMARK_INTEREST_RATE;
        this.requiredReserveRatio = Config.POLICY_REQUIRED_RESERVE_RATIO;
        this.stampDutyRate = Config.POLICY_STAMP_DUTY_RATE;
        this.capitalGainsTaxRate = Config.POLICY_CAPITAL_GAINS_TAX_RATE;
        this.dividendTaxRate = Config.POLICY_DIVIDEND_TAX_RATE;
        this.earningsDisclosurePeriodDays = Config.POLICY_EARNINGS_DISCLOSURE_DAYS;
        this.opinionCensorshipDamping = Config.POLICY_OPINION_CENSORSHIP_DAMPING;
    }

    public PolicySlot(double priceLimitRatio, double circuitBreakerThreshold,
                      double maxLeverageRatio, int settlementDays) {
        this();
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

    public double getBenchmarkInterestRate() {
        return benchmarkInterestRate;
    }

    public void setBenchmarkInterestRate(double benchmarkInterestRate) {
        this.benchmarkInterestRate = benchmarkInterestRate;
    }

    public double getRequiredReserveRatio() {
        return requiredReserveRatio;
    }

    public void setRequiredReserveRatio(double requiredReserveRatio) {
        this.requiredReserveRatio = requiredReserveRatio;
    }

    public double getStampDutyRate() {
        return stampDutyRate;
    }

    public void setStampDutyRate(double stampDutyRate) {
        this.stampDutyRate = stampDutyRate;
    }

    public double getCapitalGainsTaxRate() {
        return capitalGainsTaxRate;
    }

    public void setCapitalGainsTaxRate(double capitalGainsTaxRate) {
        this.capitalGainsTaxRate = capitalGainsTaxRate;
    }

    public double getDividendTaxRate() {
        return dividendTaxRate;
    }

    public void setDividendTaxRate(double dividendTaxRate) {
        this.dividendTaxRate = dividendTaxRate;
    }

    public int getEarningsDisclosurePeriodDays() {
        return earningsDisclosurePeriodDays;
    }

    public void setEarningsDisclosurePeriodDays(int earningsDisclosurePeriodDays) {
        this.earningsDisclosurePeriodDays = earningsDisclosurePeriodDays;
    }

    public double getOpinionCensorshipDamping() {
        return opinionCensorshipDamping;
    }

    public void setOpinionCensorshipDamping(double opinionCensorshipDamping) {
        this.opinionCensorshipDamping = opinionCensorshipDamping;
    }

    @Override
    public String toString() {
        return String.format("PolicySlot[L_limit=%.2f, Th_halt=%.2f, Lev_max=%.1f, N_settle=T+%d, r_f=%.2f%%, RRR=%.2f%%, Stamp=%.3f, CGT=%.2f, DivTax=%.2f, Disclose=%dd, Censor=%.2f]",
                priceLimitRatio, circuitBreakerThreshold, maxLeverageRatio, settlementDays,
                benchmarkInterestRate * 100.0, requiredReserveRatio * 100.0,
                stampDutyRate, capitalGainsTaxRate, dividendTaxRate,
                earningsDisclosurePeriodDays, opinionCensorshipDamping);
    }
}
