package jp.ac.tsukuba.eclab.assetmarketsimulation.market;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import sim.engine.SimState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class Stock {

    public String stockId;
    public double currentPrice;
    public double open;
    public double high;
    public double low;
    public double limitUp;
    public double limitDown;
    public double volumeThisDay = 0;
    public double turnoverThisDay = 0;
    public double totalShares;
    public double liquidShares;
    public double netAssetsPerShare;    // BPS (Book Value Per Share)
    public double eps;                  // EPS (Earnings Per Share, TTM)
    public double latestQuarterlyEps;
    public final double ipoPrice;
    public final Sector sector;
    public final double earningsGrowth; // g (growth rate)
    public final double beta;
    public double peRatioTTM;
    public double pbRatio;
    public double marketCap;
    public double liquidMarketCap;
    public double totalMarketCap;
    public double amplitude;
    public double turnoverRate;
    public double peDynamic;
    public double high52w;
    public double low52w;
    private ArrayList<Double> priceHistory52w = new ArrayList<>();
    public double peStatic;
    private final double quarterlyGrowthRate;
    private final double epsVolatility;
    private final double retainedEarningsRatio;

    public Stock(int id) {
        this.stockId = "UTEC" + String.format("%06d", id);

        this.ipoPrice = Config.nextLogNormal(
                Config.STOCK_IPO_PRICE[0], Config.STOCK_IPO_PRICE[1],
                Config.STOCK_IPO_PRICE[2], Config.STOCK_IPO_PRICE[3]
        );

        this.netAssetsPerShare = Config.nextGaussian(Config.STOCK_IPO_NET_ASSETS[0], Config.STOCK_IPO_NET_ASSETS[1], Config.STOCK_IPO_NET_ASSETS[2], Config.STOCK_IPO_NET_ASSETS[3]);
        this.eps = Config.nextGaussian(Config.STOCK_IPO_EPS[0], Config.STOCK_IPO_EPS[1], Config.STOCK_IPO_EPS[2], Config.STOCK_IPO_EPS[3]);

        this.latestQuarterlyEps = this.eps / 4.0;

        double rawLiquidShares = Config.nextGaussian(Config.STOCK_IPO_LIQUID_SHARES[0], Config.STOCK_IPO_LIQUID_SHARES[1], Config.STOCK_IPO_LIQUID_SHARES[2], Config.STOCK_IPO_LIQUID_SHARES[3]);
        this.liquidShares = Math.floor(rawLiquidShares / 100) * 100;
        this.totalShares = this.liquidShares * 1.25;

        this.sector = Sector.values()[ThreadLocalRandom.current().nextInt(Sector.values().length)];
        this.earningsGrowth = ThreadLocalRandom.current().nextDouble(Config.STOCK_FUNDAMENTALS_GROWTH_RATE_MIN, Config.STOCK_FUNDAMENTALS_GROWTH_RATE_MAX);
        this.beta = ThreadLocalRandom.current().nextDouble(Config.STOCK_FUNDAMENTALS_BETA_MIN, Config.STOCK_FUNDAMENTALS_BETA_MAX);
        this.quarterlyGrowthRate = this.earningsGrowth / 4.0;
        this.epsVolatility = Config.STOCK_FUNDAMENTALS_EPS_VOLATILITY;
        this.retainedEarningsRatio = Config.STOCK_FUNDAMENTALS_RETAINED_EARNINGS_RATIO;

        this.currentPrice = this.ipoPrice;
        this.high52w = this.ipoPrice;
        this.low52w = this.ipoPrice;
        this.priceHistory52w.add(this.ipoPrice);
        this.peStatic = -1.0;

        // Use default policy for initial limits
        updateLimits(Config.POLICY_PRICE_LIMIT_RATIO);
        resetDailyOHLC();
        updateDerivedData();
    }

    /**
     * 更新涨跌停价格 (使用 PolicySlot 的限价比例)
     */
    public void updateLimits(double priceLimitRatio) {
        this.limitUp = Math.round(this.currentPrice * (1.0 + priceLimitRatio) * 100.0) / 100.0;
        this.limitDown = Math.round(this.currentPrice * (1.0 - priceLimitRatio) * 100.0) / 100.0;
        if (this.limitDown < 0.01) this.limitDown = 0.01;
    }

    public void resetDailyOHLC() {
        this.open = this.currentPrice;
        this.high = this.currentPrice;
        this.low = this.currentPrice;
        this.volumeThisDay = 0;
        this.turnoverThisDay = 0;
        this.amplitude = 0;
        this.turnoverRate = 0;
    }

    public void updateDerivedData() {
        this.liquidMarketCap = (this.currentPrice * this.liquidShares);
        this.totalMarketCap = (this.currentPrice * this.totalShares);
        this.marketCap = this.totalMarketCap;
        this.pbRatio = (this.netAssetsPerShare > 0) ? this.currentPrice / this.netAssetsPerShare : -1;
        this.peRatioTTM = (this.eps > 0) ? this.currentPrice / this.eps : -1;
        this.peDynamic = (this.latestQuarterlyEps > 0) ? this.currentPrice / (this.latestQuarterlyEps * 4.0) : -1;
        this.turnoverRate = (this.liquidShares > 0) ? this.volumeThisDay / this.liquidShares : 0;
        this.amplitude = (this.open > 0) ? (this.high - this.low) / this.open : 0;
    }

    public void updateFundamentals(SimState state) {
        double noise = state.random.nextGaussian() * this.epsVolatility;
        double quarterlyEps = (this.eps / 4.0) * (1.0 + this.quarterlyGrowthRate + noise);
        this.latestQuarterlyEps = quarterlyEps;
        this.eps = this.eps * 0.75 + quarterlyEps;
        // 更新每股净资产 (retained earnings)
        if (this.latestQuarterlyEps > 0) {
            this.netAssetsPerShare += this.latestQuarterlyEps * this.retainedEarningsRatio;
        }
    }

    public void update52WeekHistory(double closePrice) {
        priceHistory52w.add(closePrice);
        int daysIn52Weeks = 250;
        while (priceHistory52w.size() > daysIn52Weeks) {
            priceHistory52w.remove(0);
        }
        this.high52w = Collections.max(priceHistory52w);
        this.low52w = Collections.min(priceHistory52w);
    }
}