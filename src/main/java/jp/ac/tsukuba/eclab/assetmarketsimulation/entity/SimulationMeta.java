package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

public class SimulationMeta {
    private Integer id;
    private Long seed;
    private String startTime;
    private String scenarioName;
    private Integer numStocks;
    private Integer numAgents;
    private Integer simulationDays;
    private Integer stepsPerDay;
    private String configJson;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
    public Integer getNumStocks() { return numStocks; }
    public void setNumStocks(Integer numStocks) { this.numStocks = numStocks; }
    public Integer getNumAgents() { return numAgents; }
    public void setNumAgents(Integer numAgents) { this.numAgents = numAgents; }
    public Integer getSimulationDays() { return simulationDays; }
    public void setSimulationDays(Integer simulationDays) { this.simulationDays = simulationDays; }
    public Integer getStepsPerDay() { return stepsPerDay; }
    public void setStepsPerDay(Integer stepsPerDay) { this.stepsPerDay = stepsPerDay; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
}
