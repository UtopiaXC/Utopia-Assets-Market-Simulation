package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class IpoEntity {
    private Integer stockId;
    private Double ipoPrice;
    private Double availableShares;
    private Double demandShares;
    private Double oversubscriptionRatio;
}
