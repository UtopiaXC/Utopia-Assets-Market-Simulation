package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

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

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
    public Integer getStockId() { return stockId; }
    public void setStockId(Integer stockId) { this.stockId = stockId; }
    public Double getOpen() { return open; }
    public void setOpen(Double open) { this.open = open; }
    public Double getHigh() { return high; }
    public void setHigh(Double high) { this.high = high; }
    public Double getLow() { return low; }
    public void setLow(Double low) { this.low = low; }
    public Double getClose() { return close; }
    public void setClose(Double close) { this.close = close; }
    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }
    public Double getTurnover() { return turnover; }
    public void setTurnover(Double turnover) { this.turnover = turnover; }
    public Double getPbRatio() { return pbRatio; }
    public void setPbRatio(Double pbRatio) { this.pbRatio = pbRatio; }
    public Double getPeTtm() { return peTtm; }
    public void setPeTtm(Double peTtm) { this.peTtm = peTtm; }
    public Double getPeDynamic() { return peDynamic; }
    public void setPeDynamic(Double peDynamic) { this.peDynamic = peDynamic; }
    public Double getPeStatic() { return peStatic; }
    public void setPeStatic(Double peStatic) { this.peStatic = peStatic; }
    public Double getEps() { return eps; }
    public void setEps(Double eps) { this.eps = eps; }
    public Double getNetAssets() { return netAssets; }
    public void setNetAssets(Double netAssets) { this.netAssets = netAssets; }
    public Double getTotalMarketCap() { return totalMarketCap; }
    public void setTotalMarketCap(Double totalMarketCap) { this.totalMarketCap = totalMarketCap; }
    public Double getLiquidMarketCap() { return liquidMarketCap; }
    public void setLiquidMarketCap(Double liquidMarketCap) { this.liquidMarketCap = liquidMarketCap; }
    public Double getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(Double turnoverRate) { this.turnoverRate = turnoverRate; }
    public Double getAmplitude() { return amplitude; }
    public void setAmplitude(Double amplitude) { this.amplitude = amplitude; }
    public Double getHigh52w() { return high52w; }
    public void setHigh52w(Double high52w) { this.high52w = high52w; }
    public Double getLow52w() { return low52w; }
    public void setLow52w(Double low52w) { this.low52w = low52w; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
}
