package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

/**
 * Trader analysis data
 */
public class TraderDetailDTO {

    // Basic info
    public int traderId;
    public String traderType;
    public boolean isActive;

    // Current metrics
    public TraderMetrics currentMetrics;

    // Historical data for charts
    public List<TraderDayData> history;

    // Current holdings
    public List<Holding> holdings;

    /**
     * Trader daily data
     */
    public static class TraderDayData {
        public int day;
        public double totalAssets;
        public double privateSavings;
        public double cash;
        public double reservedCash;
        public double stockValue;
        public double riskTolerance;
        public boolean isActive;

        public TraderDayData() {
        }
    }

    /**
     * Current trader metrics summary
     */
    public static class TraderMetrics {
        public double totalAssets;
        public double dailyPnl;
        public double privateSavings;
        public double cash;
        public double reservedCash;
        public double stockValue;
        public double riskTolerance;

        public TraderMetrics() {
        }

        public TraderMetrics(double totalAssets, double dailyPnl, double privateSavings,
                double cash, double reservedCash, double stockValue, double riskTolerance) {
            this.totalAssets = totalAssets;
            this.dailyPnl = dailyPnl;
            this.privateSavings = privateSavings;
            this.cash = cash;
            this.reservedCash = reservedCash;
            this.stockValue = stockValue;
            this.riskTolerance = riskTolerance;
        }
    }

    /**
     * Stock holding info
     */
    public static class Holding {
        public String stockId;
        public double quantity;
        public double price;
        public double marketValue;

        public Holding() {
        }

        public Holding(String stockId, double quantity, double price, double marketValue) {
            this.stockId = stockId;
            this.quantity = quantity;
            this.price = price;
            this.marketValue = marketValue;
        }
    }
}
