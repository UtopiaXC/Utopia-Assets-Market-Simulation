package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class StockDailyEntity {
    private Integer day;
    private Integer stockId;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;
    private Double turnover;
    private Double pbRatio;
    private Double peTtm;
    private Double peDynamic;
    private Double peStatic;
    private Double eps;
    private Double netAssets;
    private Double totalMarketCap;
    private Double liquidMarketCap;
    private Double turnoverRate;
    private Double amplitude;
    private Double high52w;
    private Double low52w;

    // Join fields
    private String stockCode;
    private String sectorName;
}
