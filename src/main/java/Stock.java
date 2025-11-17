import java.util.concurrent.ThreadLocalRandom;

public class Stock {

    public String stockId;

    public double currentPrice;
    public double open;
    public double high;
    public double low;

    public double volumeThisDay = 0;
    public double turnoverThisDay = 0;

    public final double totalShares;
    public final double liquidShares;
    public final double netAssetsPerShare;
    public final double eps;

    public double peRatioTTM;
    public double pbRatio;
    public double marketCap;
    public double liquidMarketCap;

    public Stock(int id) {
        // 【修改】股票ID改为 UTEC
        this.stockId = "UTEC" + String.format("%06d", id);

        this.totalShares = ThreadLocalRandom.current().nextDouble(1.0, 50.0);
        this.liquidShares = totalShares * ThreadLocalRandom.current().nextDouble(0.6, 1.0) * 1_0000_0000;

        this.netAssetsPerShare = ThreadLocalRandom.current().nextDouble(1.0, 15.0);
        this.eps = ThreadLocalRandom.current().nextDouble(0.01, 3.0);

        double initialPBRatio = ThreadLocalRandom.current().nextDouble(0.8, 8.0);
        this.currentPrice = this.netAssetsPerShare * initialPBRatio;
        if (this.currentPrice <= 0) this.currentPrice = 0.01;

        resetDailyOHLC();
        updateDerivedData();
    }

    public void resetDailyOHLC() {
        this.open = this.currentPrice;
        this.high = this.currentPrice;
        this.low = this.currentPrice;
        this.volumeThisDay = 0;
        this.turnoverThisDay = 0;
    }

    public void updateDerivedData() {
        this.marketCap = (this.currentPrice * this.totalShares);
        this.liquidMarketCap = (this.currentPrice * this.liquidShares) / 1_0000_0000;

        this.pbRatio = this.currentPrice / this.netAssetsPerShare;
        if (this.eps > 0) {
            this.peRatioTTM = this.currentPrice / this.eps;
        } else {
            this.peRatioTTM = -1; // 亏损
        }
    }
}