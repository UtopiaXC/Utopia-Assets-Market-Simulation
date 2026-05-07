package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class IpoSubscriptionEntity {
    private Integer stockId;
    private Integer agentId;
    private Double demandShares;
    private Double allocatedShares;
}
