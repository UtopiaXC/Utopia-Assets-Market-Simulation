package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

/**
 * Market overview data for a simulation
 */
public class MarketOverviewDTO {

    // Market K-line data
    public List<MarketDayData> klineData;

    // Current day details
    public MarketDayDetail dayDetail;

    // Top active stocks
    public List<TopStock> topActiveStocks;

    /**
     * Daily market K-line data point
     */
    public static class MarketDayData {
        public int day;
        public double open;
        public double high;
        public double low;
        public double close;
        public double volume;
        public double turnover;
        public double totalMarketCap;
        public double amplitude;
        public double turnoverRate;
        public double socialWealthPool;

        public MarketDayData() {
        }

        public MarketDayData(int day, double open, double high, double low, double close,
                double volume, double turnover, double totalMarketCap,
                double amplitude, double turnoverRate, double socialWealthPool) {
            this.day = day;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.turnover = turnover;
            this.totalMarketCap = totalMarketCap;
            this.amplitude = amplitude;
            this.turnoverRate = turnoverRate;
            this.socialWealthPool = socialWealthPool;
        }
    }

    /**
     * Market detail for a specific day
     */
    public static class MarketDayDetail {
        public int day;
        public double close;
        public double volume;
        public double turnover;
        public double turnoverRate;
        public double socialWealthPool;

        public MarketDayDetail() {
        }

        public MarketDayDetail(int day, double close, double volume, double turnover,
                double turnoverRate, double socialWealthPool) {
            this.day = day;
            this.close = close;
            this.volume = volume;
            this.turnover = turnover;
            this.turnoverRate = turnoverRate;
            this.socialWealthPool = socialWealthPool;
        }
    }

    /**
     * Top active stock info
     */
    public static class TopStock {
        public int rank;
        public String stockId;
        public String sector;
        public double close;
        public double turnover;
        public double volume;
        public double turnoverRate;

        public TopStock() {
        }

        public TopStock(int rank, String stockId, String sector, double close,
                double turnover, double volume, double turnoverRate) {
            this.rank = rank;
            this.stockId = stockId;
            this.sector = sector;
            this.close = close;
            this.turnover = turnover;
            this.volume = volume;
            this.turnoverRate = turnoverRate;
        }
    }
}
