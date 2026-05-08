package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

public class AgentAssetDailyEntity {
    private Integer day;
    private Integer agentId;
    private Double cash;
    private Double reservedCash;
    private Double privateSavings;
    private Double stockValue;
    private Double totalAssets;
    private Double riskTolerance;
    private Boolean isActive;

    // Join fields
    private String agentType;

    // Computed/aggregate fields (populated by GROUP BY queries)
    private Integer activeAgents;

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
    public Integer getAgentId() { return agentId; }
    public void setAgentId(Integer agentId) { this.agentId = agentId; }
    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }
    public Double getReservedCash() { return reservedCash; }
    public void setReservedCash(Double reservedCash) { this.reservedCash = reservedCash; }
    public Double getPrivateSavings() { return privateSavings; }
    public void setPrivateSavings(Double privateSavings) { this.privateSavings = privateSavings; }
    public Double getStockValue() { return stockValue; }
    public void setStockValue(Double stockValue) { this.stockValue = stockValue; }
    public Double getTotalAssets() { return totalAssets; }
    public void setTotalAssets(Double totalAssets) { this.totalAssets = totalAssets; }
    public Double getRiskTolerance() { return riskTolerance; }
    public void setRiskTolerance(Double riskTolerance) { this.riskTolerance = riskTolerance; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Integer getActiveAgents() { return activeAgents; }
    public void setActiveAgents(Integer activeAgents) { this.activeAgents = activeAgents; }
}
