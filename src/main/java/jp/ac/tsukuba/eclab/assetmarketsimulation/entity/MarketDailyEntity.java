package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class MarketDailyEntity {
    private Integer day;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;
    private Double turnover;
    private Double totalMarketCap;
    private Double amplitude;
    private Double turnoverRate;
    private Double socialWealthPool;
    private Integer activeAgents;
}
