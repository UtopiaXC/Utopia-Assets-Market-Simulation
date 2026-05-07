package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class HoldingsDeltaEntity {
    private Integer day;
    private Integer agentId;
    private Integer stockId;
    private Double quantityChange;
}
