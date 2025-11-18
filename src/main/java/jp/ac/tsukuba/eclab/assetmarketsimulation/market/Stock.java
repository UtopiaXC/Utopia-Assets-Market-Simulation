package jp.ac.tsukuba.eclab.assetmarketsimulation.market;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import java.util.concurrent.ThreadLocalRandom;
import sim.engine.SimState;

import java.util.ArrayList;
import java.util.Collections;

/**
 * (V4.20.1 - 修正 nextGaussian 调用)
 * (V4.19 - 修复了通胀 Bug)
 */
public class Stock {

    public String stockId;

    public double currentPrice;
    public double open;
    public double high;
    public double low;

    public double volumeThisDay = 0;
    public double turnoverThisDay = 0;

    public double totalShares;
    public double liquidShares;
    public double netAssetsPerShare;

    /** (TTM) 过去12个月 (滚动) 每股收益 (用于机构) */
    public double eps;

    /** (QoQ) 最近一个季度的每股收益 (用于散户) */
    public double latestQuarterlyEps;

    public final double ipoPrice;

    public final Sector sector;
    public final double earningsGrowth;
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

        // 【【修改 V4.20.1】】 修正 nextGaussian 调用以匹配 Config (4个参数)
        this.ipoPrice = Config.nextGaussian(
                Config.STOCK_IPO_PRICE[0], Config.STOCK_IPO_PRICE[1],
                Config.STOCK_IPO_PRICE[2], Config.STOCK_IPO_PRICE[3]);

        this.netAssetsPerShare = Config.nextGaussian(
                Config.STOCK_IPO_NET_ASSETS[0], Config.STOCK_IPO_NET_ASSETS[1],
                Config.STOCK_IPO_NET_ASSETS[2], Config.STOCK_IPO_NET_ASSETS[3]);

        this.eps = Config.nextGaussian(
                Config.STOCK_IPO_EPS[0], Config.STOCK_IPO_EPS[1],
                Config.STOCK_IPO_EPS[2], Config.STOCK_IPO_EPS[3]); // TTM EPS

        this.latestQuarterlyEps = this.eps / 4.0;

        double rawLiquidShares = Config.nextGaussian(
                Config.STOCK_IPO_LIQUID_SHARES[0], Config.STOCK_IPO_LIQUID_SHARES[1],
                Config.STOCK_IPO_LIQUID_SHARES[2], Config.STOCK_IPO_LIQUID_SHARES[3]);
        // 【【修改 V4.20.1 结束】】

        this.liquidShares = Math.floor(rawLiquidShares / 100) * 100;
        this.totalShares = this.liquidShares * 1.25;

        this.sector = Sector.values()[ThreadLocalRandom.current().nextInt(Sector.values().length)];

        this.earningsGrowth = ThreadLocalRandom.current().nextDouble(
                Config.STOCK_FUNDAMENTALS_GROWTH_RATE_MIN,
                Config.STOCK_FUNDAMENTALS_GROWTH_RATE_MAX
        );
        this.beta = ThreadLocalRandom.current().nextDouble(
                Config.STOCK_FUNDAMENTALS_BETA_MIN,
                Config.STOCK_FUNDAMENTALS_BETA_MAX
        );

        this.quarterlyGrowthRate = this.earningsGrowth / 4.0;
        this.epsVolatility = Config.STOCK_FUNDAMENTALS_EPS_VOLATILITY;
        this.retainedEarningsRatio = Config.STOCK_FUNDAMENTALS_RETAINED_EARNINGS_RATIO;

        this.currentPrice = this.ipoPrice;
        this.high52w = this.ipoPrice;
        this.low52w = this.ipoPrice;
        this.priceHistory52w.add(this.ipoPrice);
        this.peStatic = -1.0;

        resetDailyOHLC();
        updateDerivedData();
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

        this.pbRatio = this.currentPrice / this.netAssetsPerShare;

        if (this.eps > 0) {
            this.peRatioTTM = this.currentPrice / this.eps;
        } else {
            this.peRatioTTM = -1;
        }

        if (this.latestQuarterlyEps > 0) {
            this.peDynamic = this.currentPrice / (this.latestQuarterlyEps * 4.0);
        } else {
            this.peDynamic = -1;
        }

        if (this.liquidShares > 0) {
            this.turnoverRate = this.volumeThisDay / this.liquidShares;
        } else {
            this.turnoverRate = 0;
        }

        if (this.open > 0) {
            this.amplitude = (this.high - this.low) / this.open;
        } else {
            this.amplitude = 0;
        }
    }

    /**
     * (V4.19 修复 - 保持不变)
     */
    public void updateFundamentals(SimState state) {
        double noise = state.random.nextGaussian() * this.epsVolatility;
        double quarterlyEps = (this.eps / 4.0) * (1.0 + this.quarterlyGrowthRate + noise);
        this.latestQuarterlyEps = quarterlyEps;

        // (V4.19 关键修复: 移除 * 4.0)
        this.eps = this.eps * 0.75 + quarterlyEps;

        double retainedEarnings = quarterlyEps * this.retainedEarningsRatio;
        this.netAssetsPerShare += retainedEarnings;
        if (this.netAssetsPerShare < 0.01) this.netAssetsPerShare = 0.01;
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