package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class AgentEntity {
    private Integer id;
    private String agentType;
    private Double initialCash;
    private Integer maxStocks;
    private Double initialRiskTolerance;
}
