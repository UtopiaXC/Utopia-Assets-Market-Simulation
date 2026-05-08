package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

public class AgentEntity {
    private Integer id;
    private String agentType;
    private Double initialCash;
    private Integer maxStocks;
    private Double initialRiskTolerance;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Double getInitialCash() { return initialCash; }
    public void setInitialCash(Double initialCash) { this.initialCash = initialCash; }
    public Integer getMaxStocks() { return maxStocks; }
    public void setMaxStocks(Integer maxStocks) { this.maxStocks = maxStocks; }
    public Double getInitialRiskTolerance() { return initialRiskTolerance; }
    public void setInitialRiskTolerance(Double initialRiskTolerance) { this.initialRiskTolerance = initialRiskTolerance; }
}
