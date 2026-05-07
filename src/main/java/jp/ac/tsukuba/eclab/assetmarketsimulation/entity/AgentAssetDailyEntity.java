package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
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
}
