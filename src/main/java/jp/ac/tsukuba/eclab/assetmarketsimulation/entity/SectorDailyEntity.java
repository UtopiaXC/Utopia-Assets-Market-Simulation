package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

/**
 * Pre-aggregated daily sector metrics.
 * Eliminates heavy runtime GROUP BY on stock_daily during sector analysis.
 */
public class SectorDailyEntity {
    private Integer day;
    private Integer sectorId;
    private String sectorName;
    private Double totalMarketCap;
    private Double avgPe;
    private Double totalVolume;
    private Double totalTurnover;
    private Integer stockCount;

    public SectorDailyEntity() {}

    public SectorDailyEntity(Integer day, Integer sectorId, String sectorName,
                             Double totalMarketCap, Double avgPe,
                             Double totalVolume, Double totalTurnover, Integer stockCount) {
        this.day = day;
        this.sectorId = sectorId;
        this.sectorName = sectorName;
        this.totalMarketCap = totalMarketCap;
        this.avgPe = avgPe;
        this.totalVolume = totalVolume;
        this.totalTurnover = totalTurnover;
        this.stockCount = stockCount;
    }

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }

    public Integer getSectorId() { return sectorId; }
    public void setSectorId(Integer sectorId) { this.sectorId = sectorId; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public Double getTotalMarketCap() { return totalMarketCap; }
    public void setTotalMarketCap(Double totalMarketCap) { this.totalMarketCap = totalMarketCap; }

    public Double getAvgPe() { return avgPe; }
    public void setAvgPe(Double avgPe) { this.avgPe = avgPe; }

    public Double getTotalVolume() { return totalVolume; }
    public void setTotalVolume(Double totalVolume) { this.totalVolume = totalVolume; }

    public Double getTotalTurnover() { return totalTurnover; }
    public void setTotalTurnover(Double totalTurnover) { this.totalTurnover = totalTurnover; }

    public Integer getStockCount() { return stockCount; }
    public void setStockCount(Integer stockCount) { this.stockCount = stockCount; }
}
