package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

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

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public Integer getSectorId() { return sectorId; }
    public void setSectorId(Integer sectorId) { this.sectorId = sectorId; }
    public Double getIpoPrice() { return ipoPrice; }
    public void setIpoPrice(Double ipoPrice) { this.ipoPrice = ipoPrice; }
    public Double getTotalShares() { return totalShares; }
    public void setTotalShares(Double totalShares) { this.totalShares = totalShares; }
    public Double getLiquidShares() { return liquidShares; }
    public void setLiquidShares(Double liquidShares) { this.liquidShares = liquidShares; }
    public Double getInitialNetAssets() { return initialNetAssets; }
    public void setInitialNetAssets(Double initialNetAssets) { this.initialNetAssets = initialNetAssets; }
    public Double getInitialEps() { return initialEps; }
    public void setInitialEps(Double initialEps) { this.initialEps = initialEps; }
    public Double getEarningsGrowth() { return earningsGrowth; }
    public void setEarningsGrowth(Double earningsGrowth) { this.earningsGrowth = earningsGrowth; }
    public Double getBeta() { return beta; }
    public void setBeta(Double beta) { this.beta = beta; }
    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
}
