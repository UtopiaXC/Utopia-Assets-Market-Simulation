package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

public class StylizedFactsDTO {
    public String targetName;
    public Integer stockId;
    public int totalDays;
    public int sampleSize;
    public List<StockOption> availableStocks;

    // Fat Tails metrics & plots
    public FatTailsResult fatTails;

    // Volatility Clustering metrics & plots
    public VolatilityClusteringResult volatilityClustering;

    public static class StockOption {
        public int id;
        public String code;
        public String name;

        public StockOption() {}
        public StockOption(int id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }
    }

    public static class FatTailsResult {
        public double mean;
        public double stdDev;
        public double skewness;
        public double excessKurtosis;
        public double jbStat;
        public double jbPValue;
        public boolean isFatTail;
        public int outlierCount;
        public String verdict;

        // Q-Q Plot data: List of [theoreticalQuantile, sampleQuantile]
        public List<Double[]> qqPlotPoints;
        public Double[] qqReferenceLine;

        // Density Histogram data
        public List<HistogramBin> histogramBins;
        public List<Double[]> normalCurve;

        // Tail CCDF Log-Log: List of [x, sampleCcdf, normalCcdf]
        public List<Double[]> tailCcdfPoints;

        // Returns & Extreme Shocks Timeline
        public List<ReturnPoint> returnsTimeline;

        // Rolling Excess Kurtosis Timeline
        public List<RollingStatPoint> rollingStats;
    }

    public static class HistogramBin {
        public double binCenter;
        public double binStart;
        public double binEnd;
        public double density;
        public double normalDensity;
        public int count;

        public HistogramBin() {}
        public HistogramBin(double binCenter, double binStart, double binEnd, double density, double normalDensity, int count) {
            this.binCenter = binCenter;
            this.binStart = binStart;
            this.binEnd = binEnd;
            this.density = density;
            this.normalDensity = normalDensity;
            this.count = count;
        }
    }

    public static class ReturnPoint {
        public int day;
        public double value;
        public boolean isOutlier;

        public ReturnPoint() {}
        public ReturnPoint(int day, double value, boolean isOutlier) {
            this.day = day;
            this.value = value;
            this.isOutlier = isOutlier;
        }
    }

    public static class RollingStatPoint {
        public int day;
        public double rollingKurtosis;
        public double rollingSkewness;

        public RollingStatPoint() {}
        public RollingStatPoint(int day, double rollingKurtosis, double rollingSkewness) {
            this.day = day;
            this.rollingKurtosis = rollingKurtosis;
            this.rollingSkewness = rollingSkewness;
        }
    }

    public static class VolatilityClusteringResult {
        public double archLmStat;
        public double archLmPValue;
        public int archLags;
        public boolean hasArchEffect;
        public String verdict;

        public List<Integer> lags;
        public List<Double> rawReturnsAcf;
        public List<Double> absReturnsAcf;
        public List<Double> squaredReturnsAcf;
        public double upperConfidenceBand;
        public double lowerConfidenceBand;

        // Volatility Clustering Timeline: [day, absReturn, rollingVol]
        public List<VolTimelinePoint> volatilityTimeline;

        // Log-Log Volatility Autocorrelation Decay: [log10Lag, log10AbsAcf]
        public List<Double[]> logAcfPoints;

        // ARCH Lag-1 Phase Space: [e_{t-1}^2, e_t^2]
        public List<Double[]> archScatterPoints;
        public List<Double[]> archTrendLine;
    }

    public static class VolTimelinePoint {
        public int day;
        public double absReturn;
        public double rollingVol;

        public VolTimelinePoint() {}
        public VolTimelinePoint(int day, double absReturn, double rollingVol) {
            this.day = day;
            this.absReturn = absReturn;
            this.rollingVol = rollingVol;
        }
    }
}
