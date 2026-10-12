package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

/**
 * Pre-aggregated daily agent type metrics.
 * Eliminates heavy runtime GROUP BY on agent_asset_daily and JOIN agent during macro analysis.
 */
public class AgentTypeDailyEntity {
    private Integer day;
    private String agentType;
    private Double totalAssets;
    private Double avgRiskTolerance;
    private Integer activeCount;
    private Integer totalCount;
    private Double cash;
    private Double stockValue;
    private Double privateSavings;

    public AgentTypeDailyEntity() {}

    public AgentTypeDailyEntity(Integer day, String agentType, Double totalAssets,
                                Double avgRiskTolerance, Integer activeCount, Integer totalCount,
                                Double cash, Double stockValue, Double privateSavings) {
        this.day = day;
        this.agentType = agentType;
        this.totalAssets = totalAssets;
        this.avgRiskTolerance = avgRiskTolerance;
        this.activeCount = activeCount;
        this.totalCount = totalCount;
        this.cash = cash;
        this.stockValue = stockValue;
        this.privateSavings = privateSavings;
    }

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public Double getTotalAssets() { return totalAssets; }
    public void setTotalAssets(Double totalAssets) { this.totalAssets = totalAssets; }

    public Double getAvgRiskTolerance() { return avgRiskTolerance; }
    public void setAvgRiskTolerance(Double avgRiskTolerance) { this.avgRiskTolerance = avgRiskTolerance; }

    public Integer getActiveCount() { return activeCount; }
    public void setActiveCount(Integer activeCount) { this.activeCount = activeCount; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }

    public Double getStockValue() { return stockValue; }
    public void setStockValue(Double stockValue) { this.stockValue = stockValue; }

    public Double getPrivateSavings() { return privateSavings; }
    public void setPrivateSavings(Double privateSavings) { this.privateSavings = privateSavings; }
}
