package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

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

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
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
    public Double getTotalMarketCap() { return totalMarketCap; }
    public void setTotalMarketCap(Double totalMarketCap) { this.totalMarketCap = totalMarketCap; }
    public Double getAmplitude() { return amplitude; }
    public void setAmplitude(Double amplitude) { this.amplitude = amplitude; }
    public Double getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(Double turnoverRate) { this.turnoverRate = turnoverRate; }
    public Double getSocialWealthPool() { return socialWealthPool; }
    public void setSocialWealthPool(Double socialWealthPool) { this.socialWealthPool = socialWealthPool; }
    public Integer getActiveAgents() { return activeAgents; }
    public void setActiveAgents(Integer activeAgents) { this.activeAgents = activeAgents; }
}
