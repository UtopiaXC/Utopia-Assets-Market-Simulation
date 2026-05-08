package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

public class HoldingsSnapshotEntity {
    private Integer snapshotDay;
    private Integer agentId;
    /** JSON map: {"stockId": quantity, ...} */
    private String holdingsJson;

    public Integer getSnapshotDay() { return snapshotDay; }
    public void setSnapshotDay(Integer snapshotDay) { this.snapshotDay = snapshotDay; }
    public Integer getAgentId() { return agentId; }
    public void setAgentId(Integer agentId) { this.agentId = agentId; }
    public String getHoldingsJson() { return holdingsJson; }
    public void setHoldingsJson(String holdingsJson) { this.holdingsJson = holdingsJson; }
}
