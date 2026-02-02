package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

/**
 * Stock analysis data
 */
public class StockDetailDTO {

    // Basic info
    public String stockId;
    public String sector;

    // Current metrics
    public StockMetrics currentMetrics;

    // Historical data for charts
    public List<StockDayData> history;

    // Top shareholders
    public List<Shareholder> shareholders;

    /**
     * Stock daily data for K-line and other charts
     */
    public static class StockDayData {
        public int day;
        public double open;
        public double high;
        public double low;
        public double close;
        public double volume;
        public double turnover;
        public double pbRatio;
        public double peTtm;
        public double peStatic;
        public double peDynamic;
        public double eps;
        public double netAssets;
        public double totalMarketCap;
        public double liquidMarketCap;
        public double turnoverRate;
        public double amplitude;
        public double totalShares;
        public double liquidShares;
        public double high52w;
        public double low52w;

        public StockDayData() {
        }
    }

    /**
     * Current stock metrics summary
     */
    public static class StockMetrics {
        public double close;
        public double peTtm;
        public double pbRatio;
        public double totalMarketCap;
        public double volume;
        public double turnoverRate;

        public StockMetrics() {
        }

        public StockMetrics(double close, double peTtm, double pbRatio,
                double totalMarketCap, double volume, double turnoverRate) {
            this.close = close;
            this.peTtm = peTtm;
            this.pbRatio = pbRatio;
            this.totalMarketCap = totalMarketCap;
            this.volume = volume;
            this.turnoverRate = turnoverRate;
        }
    }

    /**
     * Stock shareholder info
     */
    public static class Shareholder {
        public int traderId;
        public String traderType;
        public double quantity;
        public double value;

        public Shareholder() {
        }

        public Shareholder(int traderId, String traderType, double quantity, double value) {
            this.traderId = traderId;
            this.traderType = traderType;
            this.quantity = quantity;
            this.value = value;
        }
    }
}
