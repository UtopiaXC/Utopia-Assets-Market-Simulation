package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 仿真全量动态配置项 (支持前端完整配置与预设载入)
 * 映射并替代静态 Config，实现宏观市场、估值三因子、分层社交网络、政策插槽及日志级别的全面可配置化
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationConfig {

    // ============ 1. 基础市场与运行参数 ============
    private String simulationName = null;
    private String scenarioName = "DefaultScenario";
    private int numStocks = Config.MARKET_NUM_STOCKS;
    private int simulationDays = Config.MARKET_SIMULATION_DAYS;
    private int stepsPerDay = Config.MARKET_STEPS_PER_DAY;
    private double indexBase = Config.MARKET_INDEX_BASE;
    private long stepDelay = 0; // ms

    // ============ 2. 宏观经济与资金池 ============
    private double totalCapitalPool = Config.ECONOMY_TOTAL_CAPITAL_POOL;
    private int totalAgents = Config.ECONOMY_TOTAL_AGENTS;
    private double socialPoolRatio = Config.ECONOMY_SOCIAL_POOL_RATIO;

    // ============ 3. 交易者人口分布与演化 ============
    private double institutionalRatio = Config.AGENT_INSTITUTIONAL_POPULATION_RATIO;
    private double institutionalCapitalRatio = Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO;
    private double retailSubRatio = Config.AGENT_RETAIL_SUB_RATIO;
    private double noiseSubRatio = Config.AGENT_NOISE_SUB_RATIO;
    private double agentMutationRate = Config.AGENT_MUTATION_RATE;
    private double agentMutationStdDev = Config.AGENT_MUTATION_STDDEV;

    // ============ 4. 政策插槽参数 (Policy Slots) ============
    private double priceLimitRatio = Config.POLICY_PRICE_LIMIT_RATIO;
    private double circuitBreakerThreshold = Config.POLICY_CIRCUIT_BREAKER_THRESHOLD;
    private double maxLeverageRatio = Config.POLICY_MAX_LEVERAGE_RATIO;
    private int settlementDays = Config.POLICY_SETTLEMENT_DAYS;

    // ============ 5. 杠杆与配资风控 ============
    private double leverageMarginCallRatio = Config.LEVERAGE_MARGIN_CALL_RATIO;
    private double leverageInterestRateDaily = Config.LEVERAGE_INTEREST_RATE_DAILY;
    private double leverageRequestProb = Config.LEVERAGE_REQUEST_PROB;

    // ============ 6. 估值乘数与板块加成 ============
    private double pbMultiplier = Config.VALUATION_PB_MULTIPLIER;
    private double peMultiplier = Config.VALUATION_PE_MULTIPLIER;
    private double sectorBonusTech = Config.VALUATION_SECTOR_TECH;
    private double sectorBonusHealthcare = Config.VALUATION_SECTOR_HEALTHCARE;
    private double sectorBonusConsumer = Config.VALUATION_SECTOR_CONSUMER;
    private double sectorBonusFinance = Config.VALUATION_SECTOR_FINANCE;
    private double sectorBonusIndustry = Config.VALUATION_SECTOR_INDUSTRY;
    private double sectorBonusEnergy = Config.VALUATION_SECTOR_ENERGY;

    // ============ 7. 三因子估值基准权重与交易频率 ============
    // 机构交易者 (Institutional)
    private double valFundWeightInst = Config.VALUATION_FUND_WEIGHT_INST;
    private double valSocialWeightInst = Config.VALUATION_SOCIAL_WEIGHT_INST;
    private double valTrendWeightInst = Config.VALUATION_TREND_WEIGHT_INST;
    private double valNoiseStdDevInst = Config.VALUATION_NOISE_STDDEV_INST;
    private int valLookbackDaysInst = Config.VALUATION_LOOKBACK_DAYS_INST;
    private int valTradeIntervalMinInst = Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MIN_DAYS;
    private int valTradeIntervalMaxInst = Config.AGENT_INSTITUTIONAL_TRADE_INTERVAL_MAX_DAYS;

    // 散户交易者 (Retail)
    private double valFundWeightRetail = Config.VALUATION_FUND_WEIGHT_RETAIL;
    private double valSocialWeightRetail = Config.VALUATION_SOCIAL_WEIGHT_RETAIL;
    private double valTrendWeightRetail = Config.VALUATION_TREND_WEIGHT_RETAIL;
    private double valNoiseStdDevRetail = Config.VALUATION_NOISE_STDDEV_RETAIL;
    private int valLookbackDaysRetail = Config.VALUATION_LOOKBACK_DAYS_RETAIL;
    private int valTradeIntervalMinRetail = Config.AGENT_RETAIL_TRADE_INTERVAL_MIN_DAYS;
    private int valTradeIntervalMaxRetail = Config.AGENT_RETAIL_TRADE_INTERVAL_MAX_DAYS;

    // 噪声交易者 (Noise)
    private double valFundWeightNoise = Config.VALUATION_FUND_WEIGHT_NOISE;
    private double valSocialWeightNoise = Config.VALUATION_SOCIAL_WEIGHT_NOISE;
    private double valTrendWeightNoise = Config.VALUATION_TREND_WEIGHT_NOISE;
    private double valNoiseStdDevNoise = Config.VALUATION_NOISE_STDDEV_NOISE;
    private int valLookbackDaysNoise = Config.VALUATION_LOOKBACK_DAYS_NOISE;
    private int valTradeIntervalMinNoise = Config.AGENT_NOISE_TRADE_INTERVAL_MIN_DAYS;
    private int valTradeIntervalMaxNoise = Config.AGENT_NOISE_TRADE_INTERVAL_MAX_DAYS;

    // ============ 8. 社交网络与信誉评级 ============
    private int socialTopK = Config.SOCIAL_TOP_K_NEIGHBORS;
    private int socialInstNeighborsCount = Config.SOCIAL_INST_NEIGHBORS_COUNT;
    private int socialPeerNeighborsCount = Config.SOCIAL_PEER_NEIGHBORS_COUNT;
    private double socialInstInfluenceRatio = Config.SOCIAL_INST_INFLUENCE_RATIO;
    private double socialSoftmaxTemperature = Config.SOCIAL_SOFTMAX_TEMPERATURE;
    private double socialMinWeightFloor = Config.SOCIAL_MIN_WEIGHT_FLOOR;
    private double socialAlphaEmaLambda = Config.SOCIAL_ALPHA_EMA_LAMBDA;
    private double socialVarianceEmaLambda = Config.SOCIAL_VARIANCE_EMA_LAMBDA;
    private int socialReputationWarmupDays = Config.SOCIAL_REPUTATION_WARMUP_DAYS;
    private double socialReputationScaleKappa = Config.SOCIAL_REPUTATION_SCALE_KAPPA;
    private double socialSensitivityBeta = Config.SOCIAL_SENSITIVITY_BETA;
    private int socialRebuildInterval = Config.SOCIAL_NETWORK_REBUILD_INTERVAL;

    // ============ 9. 数据记录与 I/O 优化 ============
    private boolean logTrades = true;
    private boolean logHoldingsDelta = true;
    private boolean logAgentDaily = true;
    private int logSampleInterval = 1;
    private int holdingsSnapshotInterval = 10;

    public SimulationConfig() {
    }

    public static SimulationConfig fromDefaults() {
        return new SimulationConfig();
    }

    // ==========================================
    // Getters & Setters
    // ==========================================

    public String getSimulationName() { return simulationName; }
    public void setSimulationName(String simulationName) { this.simulationName = simulationName; }

    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

    public int getNumStocks() { return numStocks; }
    public void setNumStocks(int numStocks) { this.numStocks = numStocks; }

    public int getSimulationDays() { return simulationDays; }
    public void setSimulationDays(int simulationDays) { this.simulationDays = simulationDays; }

    public int getStepsPerDay() { return stepsPerDay; }
    public void setStepsPerDay(int stepsPerDay) { this.stepsPerDay = stepsPerDay; }

    public double getIndexBase() { return indexBase; }
    public void setIndexBase(double indexBase) { this.indexBase = indexBase; }

    public long getStepDelay() { return stepDelay; }
    public void setStepDelay(long stepDelay) { this.stepDelay = stepDelay; }

    public double getTotalCapitalPool() { return totalCapitalPool; }
    public void setTotalCapitalPool(double totalCapitalPool) { this.totalCapitalPool = totalCapitalPool; }

    public int getTotalAgents() { return totalAgents; }
    public void setTotalAgents(int totalAgents) { this.totalAgents = totalAgents; }

    public double getSocialPoolRatio() { return socialPoolRatio; }
    public void setSocialPoolRatio(double socialPoolRatio) { this.socialPoolRatio = socialPoolRatio; }

    public double getInstitutionalRatio() { return institutionalRatio; }
    public void setInstitutionalRatio(double institutionalRatio) { this.institutionalRatio = institutionalRatio; }

    public double getInstitutionalCapitalRatio() { return institutionalCapitalRatio; }
    public void setInstitutionalCapitalRatio(double institutionalCapitalRatio) { this.institutionalCapitalRatio = institutionalCapitalRatio; }

    public double getRetailSubRatio() { return retailSubRatio; }
    public void setRetailSubRatio(double retailSubRatio) { this.retailSubRatio = retailSubRatio; }

    public double getNoiseSubRatio() { return noiseSubRatio; }
    public void setNoiseSubRatio(double noiseSubRatio) { this.noiseSubRatio = noiseSubRatio; }

    public double getAgentMutationRate() { return agentMutationRate; }
    public void setAgentMutationRate(double agentMutationRate) { this.agentMutationRate = agentMutationRate; }

    public double getAgentMutationStdDev() { return agentMutationStdDev; }
    public void setAgentMutationStdDev(double agentMutationStdDev) { this.agentMutationStdDev = agentMutationStdDev; }

    public double getPriceLimitRatio() { return priceLimitRatio; }
    public void setPriceLimitRatio(double priceLimitRatio) { this.priceLimitRatio = priceLimitRatio; }

    public double getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(double circuitBreakerThreshold) { this.circuitBreakerThreshold = circuitBreakerThreshold; }

    public double getMaxLeverageRatio() { return maxLeverageRatio; }
    public void setMaxLeverageRatio(double maxLeverageRatio) { this.maxLeverageRatio = maxLeverageRatio; }

    public int getSettlementDays() { return settlementDays; }
    public void setSettlementDays(int settlementDays) { this.settlementDays = settlementDays; }

    public double getLeverageMarginCallRatio() { return leverageMarginCallRatio; }
    public void setLeverageMarginCallRatio(double leverageMarginCallRatio) { this.leverageMarginCallRatio = leverageMarginCallRatio; }

    public double getLeverageInterestRateDaily() { return leverageInterestRateDaily; }
    public void setLeverageInterestRateDaily(double leverageInterestRateDaily) { this.leverageInterestRateDaily = leverageInterestRateDaily; }

    public double getLeverageRequestProb() { return leverageRequestProb; }
    public void setLeverageRequestProb(double leverageRequestProb) { this.leverageRequestProb = leverageRequestProb; }

    public double getPbMultiplier() { return pbMultiplier; }
    public void setPbMultiplier(double pbMultiplier) { this.pbMultiplier = pbMultiplier; }

    public double getPeMultiplier() { return peMultiplier; }
    public void setPeMultiplier(double peMultiplier) { this.peMultiplier = peMultiplier; }

    public double getSectorBonusTech() { return sectorBonusTech; }
    public void setSectorBonusTech(double sectorBonusTech) { this.sectorBonusTech = sectorBonusTech; }

    public double getSectorBonusHealthcare() { return sectorBonusHealthcare; }
    public void setSectorBonusHealthcare(double sectorBonusHealthcare) { this.sectorBonusHealthcare = sectorBonusHealthcare; }

    public double getSectorBonusConsumer() { return sectorBonusConsumer; }
    public void setSectorBonusConsumer(double sectorBonusConsumer) { this.sectorBonusConsumer = sectorBonusConsumer; }

    public double getSectorBonusFinance() { return sectorBonusFinance; }
    public void setSectorBonusFinance(double sectorBonusFinance) { this.sectorBonusFinance = sectorBonusFinance; }

    public double getSectorBonusIndustry() { return sectorBonusIndustry; }
    public void setSectorBonusIndustry(double sectorBonusIndustry) { this.sectorBonusIndustry = sectorBonusIndustry; }

    public double getSectorBonusEnergy() { return sectorBonusEnergy; }
    public void setSectorBonusEnergy(double sectorBonusEnergy) { this.sectorBonusEnergy = sectorBonusEnergy; }

    public double getValFundWeightInst() { return valFundWeightInst; }
    public void setValFundWeightInst(double valFundWeightInst) { this.valFundWeightInst = valFundWeightInst; }

    public double getValSocialWeightInst() { return valSocialWeightInst; }
    public void setValSocialWeightInst(double valSocialWeightInst) { this.valSocialWeightInst = valSocialWeightInst; }

    public double getValTrendWeightInst() { return valTrendWeightInst; }
    public void setValTrendWeightInst(double valTrendWeightInst) { this.valTrendWeightInst = valTrendWeightInst; }

    public double getValNoiseStdDevInst() { return valNoiseStdDevInst; }
    public void setValNoiseStdDevInst(double valNoiseStdDevInst) { this.valNoiseStdDevInst = valNoiseStdDevInst; }

    public int getValLookbackDaysInst() { return valLookbackDaysInst; }
    public void setValLookbackDaysInst(int valLookbackDaysInst) { this.valLookbackDaysInst = valLookbackDaysInst; }

    public double getValFundWeightRetail() { return valFundWeightRetail; }
    public void setValFundWeightRetail(double valFundWeightRetail) { this.valFundWeightRetail = valFundWeightRetail; }

    public double getValSocialWeightRetail() { return valSocialWeightRetail; }
    public void setValSocialWeightRetail(double valSocialWeightRetail) { this.valSocialWeightRetail = valSocialWeightRetail; }

    public double getValTrendWeightRetail() { return valTrendWeightRetail; }
    public void setValTrendWeightRetail(double valTrendWeightRetail) { this.valTrendWeightRetail = valTrendWeightRetail; }

    public double getValNoiseStdDevRetail() { return valNoiseStdDevRetail; }
    public void setValNoiseStdDevRetail(double valNoiseStdDevRetail) { this.valNoiseStdDevRetail = valNoiseStdDevRetail; }

    public int getValLookbackDaysRetail() { return valLookbackDaysRetail; }
    public void setValLookbackDaysRetail(int valLookbackDaysRetail) { this.valLookbackDaysRetail = valLookbackDaysRetail; }

    public double getValFundWeightNoise() { return valFundWeightNoise; }
    public void setValFundWeightNoise(double valFundWeightNoise) { this.valFundWeightNoise = valFundWeightNoise; }

    public double getValSocialWeightNoise() { return valSocialWeightNoise; }
    public void setValSocialWeightNoise(double valSocialWeightNoise) { this.valSocialWeightNoise = valSocialWeightNoise; }

    public double getValTrendWeightNoise() { return valTrendWeightNoise; }
    public void setValTrendWeightNoise(double valTrendWeightNoise) { this.valTrendWeightNoise = valTrendWeightNoise; }

    public double getValNoiseStdDevNoise() { return valNoiseStdDevNoise; }
    public void setValNoiseStdDevNoise(double valNoiseStdDevNoise) { this.valNoiseStdDevNoise = valNoiseStdDevNoise; }

    public int getValLookbackDaysNoise() { return valLookbackDaysNoise; }
    public void setValLookbackDaysNoise(int valLookbackDaysNoise) { this.valLookbackDaysNoise = valLookbackDaysNoise; }

    public int getValTradeIntervalMinInst() { return valTradeIntervalMinInst; }
    public void setValTradeIntervalMinInst(int valTradeIntervalMinInst) { this.valTradeIntervalMinInst = valTradeIntervalMinInst; }

    public int getValTradeIntervalMaxInst() { return valTradeIntervalMaxInst; }
    public void setValTradeIntervalMaxInst(int valTradeIntervalMaxInst) { this.valTradeIntervalMaxInst = valTradeIntervalMaxInst; }

    public int getValTradeIntervalMinRetail() { return valTradeIntervalMinRetail; }
    public void setValTradeIntervalMinRetail(int valTradeIntervalMinRetail) { this.valTradeIntervalMinRetail = valTradeIntervalMinRetail; }

    public int getValTradeIntervalMaxRetail() { return valTradeIntervalMaxRetail; }
    public void setValTradeIntervalMaxRetail(int valTradeIntervalMaxRetail) { this.valTradeIntervalMaxRetail = valTradeIntervalMaxRetail; }

    public int getValTradeIntervalMinNoise() { return valTradeIntervalMinNoise; }
    public void setValTradeIntervalMinNoise(int valTradeIntervalMinNoise) { this.valTradeIntervalMinNoise = valTradeIntervalMinNoise; }

    public int getValTradeIntervalMaxNoise() { return valTradeIntervalMaxNoise; }
    public void setValTradeIntervalMaxNoise(int valTradeIntervalMaxNoise) { this.valTradeIntervalMaxNoise = valTradeIntervalMaxNoise; }

    public int getSocialTopK() { return socialTopK; }
    public void setSocialTopK(int socialTopK) { this.socialTopK = socialTopK; }

    public int getSocialInstNeighborsCount() { return socialInstNeighborsCount; }
    public void setSocialInstNeighborsCount(int socialInstNeighborsCount) { this.socialInstNeighborsCount = socialInstNeighborsCount; }

    public int getSocialPeerNeighborsCount() { return socialPeerNeighborsCount; }
    public void setSocialPeerNeighborsCount(int socialPeerNeighborsCount) { this.socialPeerNeighborsCount = socialPeerNeighborsCount; }

    public double getSocialInstInfluenceRatio() { return socialInstInfluenceRatio; }
    public void setSocialInstInfluenceRatio(double socialInstInfluenceRatio) { this.socialInstInfluenceRatio = socialInstInfluenceRatio; }

    public double getSocialSoftmaxTemperature() { return socialSoftmaxTemperature; }
    public void setSocialSoftmaxTemperature(double socialSoftmaxTemperature) { this.socialSoftmaxTemperature = socialSoftmaxTemperature; }

    public double getSocialMinWeightFloor() { return socialMinWeightFloor; }
    public void setSocialMinWeightFloor(double socialMinWeightFloor) { this.socialMinWeightFloor = socialMinWeightFloor; }

    public double getSocialAlphaEmaLambda() { return socialAlphaEmaLambda; }
    public void setSocialAlphaEmaLambda(double socialAlphaEmaLambda) { this.socialAlphaEmaLambda = socialAlphaEmaLambda; }

    public double getSocialVarianceEmaLambda() { return socialVarianceEmaLambda; }
    public void setSocialVarianceEmaLambda(double socialVarianceEmaLambda) { this.socialVarianceEmaLambda = socialVarianceEmaLambda; }

    public int getSocialReputationWarmupDays() { return socialReputationWarmupDays; }
    public void setSocialReputationWarmupDays(int socialReputationWarmupDays) { this.socialReputationWarmupDays = socialReputationWarmupDays; }

    public double getSocialReputationScaleKappa() { return socialReputationScaleKappa; }
    public void setSocialReputationScaleKappa(double socialReputationScaleKappa) { this.socialReputationScaleKappa = socialReputationScaleKappa; }

    public double getSocialSensitivityBeta() { return socialSensitivityBeta; }
    public void setSocialSensitivityBeta(double socialSensitivityBeta) { this.socialSensitivityBeta = socialSensitivityBeta; }

    public int getSocialRebuildInterval() { return socialRebuildInterval; }
    public void setSocialRebuildInterval(int socialRebuildInterval) { this.socialRebuildInterval = socialRebuildInterval; }

    public boolean isLogTrades() { return logTrades; }
    public void setLogTrades(boolean logTrades) { this.logTrades = logTrades; }

    public boolean isLogHoldingsDelta() { return logHoldingsDelta; }
    public void setLogHoldingsDelta(boolean logHoldingsDelta) { this.logHoldingsDelta = logHoldingsDelta; }

    public boolean isLogAgentDaily() { return logAgentDaily; }
    public void setLogAgentDaily(boolean logAgentDaily) { this.logAgentDaily = logAgentDaily; }

    public int getLogSampleInterval() { return logSampleInterval; }
    public void setLogSampleInterval(int logSampleInterval) { this.logSampleInterval = logSampleInterval; }

    public int getHoldingsSnapshotInterval() { return holdingsSnapshotInterval; }
    public void setHoldingsSnapshotInterval(int holdingsSnapshotInterval) { this.holdingsSnapshotInterval = holdingsSnapshotInterval; }
}
