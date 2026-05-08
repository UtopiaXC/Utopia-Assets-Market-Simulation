package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

public class HoldingsDeltaEntity {
    private Integer day;
    private Integer agentId;
    private Integer stockId;
    private Double quantityChange;

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
    public Integer getAgentId() { return agentId; }
    public void setAgentId(Integer agentId) { this.agentId = agentId; }
    public Integer getStockId() { return stockId; }
    public void setStockId(Integer stockId) { this.stockId = stockId; }
    public Double getQuantityChange() { return quantityChange; }
    public void setQuantityChange(Double quantityChange) { this.quantityChange = quantityChange; }
}
