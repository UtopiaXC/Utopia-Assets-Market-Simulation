package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class StockEntity {
    private Integer id;
    private String stockCode;
    private Integer sectorId;
    private Double ipoPrice;
    private Double totalShares;
    private Double liquidShares;
    private Double initialNetAssets;
    private Double initialEps;
    private Double earningsGrowth;
    private Double beta;

    // Join fields (not stored, populated by queries)
    private String sectorName;
}
