package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dynamic simulation configuration
 * Replaces static Config for front-end controllable parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationConfig {

    // ============ Market Parameters ============
    private int numStocks = Config.MARKET_NUM_STOCKS;
    private int simulationDays = Config.MARKET_SIMULATION_DAYS;
    private double indexBase = Config.MARKET_INDEX_BASE;
    private double priceLimitRatio = Config.MARKET_PRICE_LIMIT_RATIO;

    // ============ Time Parameters ============
    private int stepsPerDay = Config.MARKET_STEPS_PER_DAY;

    // ============ Economy Parameters ============
    private double totalCapitalPool = Config.ECONOMY_TOTAL_CAPITAL_POOL;
    private int totalAgents = Config.ECONOMY_TOTAL_AGENTS;
    private double socialPoolRatio = Config.ECONOMY_SOCIAL_POOL_RATIO;

    // ============ Agent Ratios ============
    private double institutionalRatio = Config.AGENT_INSTITUTIONAL_POPULATION_RATIO;
    private double institutionalCapitalRatio = Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO;
    private double retailSubRatio = Config.AGENT_RETAIL_SUB_RATIO;
    private double noiseSubRatio = Config.AGENT_NOISE_SUB_RATIO;

    // ============ Scenario ============
    private String scenarioName = "EmptyScenario";

    // ============ Logging Settings ============
    // ============ Logging Settings ============
    private int logSampleInterval = 1; // Log every N days (1 = every day)
    private boolean logHoldingsDelta = true; // Only log holding changes
    private int holdingsSnapshotInterval = 10; // Full snapshot every N days

    // ============ Execution Parameters ============
    private String simulationName = null; // Custom name for the simulation result
    private long stepDelay = 1000; // Base delay in milliseconds between steps

    // ============ Constructors ============
    public SimulationConfig() {
    }

    public static SimulationConfig fromDefaults() {
        return new SimulationConfig();
    }

    // ============ Getters and Setters ============
    public int getNumStocks() {
        return numStocks;
    }

    public void setNumStocks(int numStocks) {
        this.numStocks = numStocks;
    }

    public int getSimulationDays() {
        return simulationDays;
    }

    public void setSimulationDays(int simulationDays) {
        this.simulationDays = simulationDays;
    }

    public double getIndexBase() {
        return indexBase;
    }

    public void setIndexBase(double indexBase) {
        this.indexBase = indexBase;
    }

    public double getPriceLimitRatio() {
        return priceLimitRatio;
    }

    public void setPriceLimitRatio(double priceLimitRatio) {
        this.priceLimitRatio = priceLimitRatio;
    }

    public int getStepsPerDay() {
        return stepsPerDay;
    }

    public void setStepsPerDay(int stepsPerDay) {
        this.stepsPerDay = stepsPerDay;
    }

    public double getTotalCapitalPool() {
        return totalCapitalPool;
    }

    public void setTotalCapitalPool(double totalCapitalPool) {
        this.totalCapitalPool = totalCapitalPool;
    }

    public int getTotalAgents() {
        return totalAgents;
    }

    public void setTotalAgents(int totalAgents) {
        this.totalAgents = totalAgents;
    }

    public double getSocialPoolRatio() {
        return socialPoolRatio;
    }

    public void setSocialPoolRatio(double socialPoolRatio) {
        this.socialPoolRatio = socialPoolRatio;
    }

    public double getInstitutionalRatio() {
        return institutionalRatio;
    }

    public void setInstitutionalRatio(double institutionalRatio) {
        this.institutionalRatio = institutionalRatio;
    }

    public double getInstitutionalCapitalRatio() {
        return institutionalCapitalRatio;
    }

    public void setInstitutionalCapitalRatio(double ratio) {
        this.institutionalCapitalRatio = ratio;
    }

    public double getRetailSubRatio() {
        return retailSubRatio;
    }

    public void setRetailSubRatio(double retailSubRatio) {
        this.retailSubRatio = retailSubRatio;
    }

    public double getNoiseSubRatio() {
        return noiseSubRatio;
    }

    public void setNoiseSubRatio(double noiseSubRatio) {
        this.noiseSubRatio = noiseSubRatio;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public int getLogSampleInterval() {
        return logSampleInterval;
    }

    public void setLogSampleInterval(int logSampleInterval) {
        this.logSampleInterval = logSampleInterval;
    }

    public boolean isLogHoldingsDelta() {
        return logHoldingsDelta;
    }

    public void setLogHoldingsDelta(boolean logHoldingsDelta) {
        this.logHoldingsDelta = logHoldingsDelta;
    }

    public int getHoldingsSnapshotInterval() {
        return holdingsSnapshotInterval;
    }

    public void setHoldingsSnapshotInterval(int interval) {
        this.holdingsSnapshotInterval = interval;
    }

    public String getSimulationName() {
        return simulationName;
    }

    public void setSimulationName(String simulationName) {
        this.simulationName = simulationName;
    }

    public long getStepDelay() {
        return stepDelay;
    }

    public void setStepDelay(long stepDelay) {
        this.stepDelay = stepDelay;
    }
}
