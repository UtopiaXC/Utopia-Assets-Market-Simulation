package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StylizedFactsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StylizedFactsDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.MarketDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.MarketDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StylizedFactsService {

    @Autowired
    private DatabaseService databaseService;

    public StylizedFactsDTO getStylizedFacts(String dbFile, Integer stockId) {
        StylizedFactsDTO result = new StylizedFactsDTO();
        result.stockId = (stockId != null && stockId >= 0) ? stockId : null;

        try (SqlSession session = databaseService.openSession(dbFile)) {
            StockMapper stockMapper = session.getMapper(StockMapper.class);
            List<StockEntity> stocks = stockMapper.selectAll();
            result.availableStocks = new ArrayList<>();
            if (stocks != null) {
                for (StockEntity s : stocks) {
                    result.availableStocks.add(new StockOption(s.getId(), s.getStockCode(), s.getStockCode() + " (" + s.getSectorName() + ")"));
                }
            }

            List<Double> prices = new ArrayList<>();
            if (stockId != null && stockId >= 0) {
                StockDailyMapper stockDailyMapper = session.getMapper(StockDailyMapper.class);
                List<StockDailyEntity> dailyList = stockDailyMapper.selectByStockId(stockId);
                String code = "Stock #" + stockId;
                if (stocks != null) {
                    for (StockEntity s : stocks) {
                        if (s.getId() == stockId) {
                            code = s.getStockCode() + " (" + s.getSectorName() + ")";
                            break;
                        }
                    }
                }
                result.targetName = code;
                if (dailyList != null) {
                    for (StockDailyEntity sde : dailyList) {
                        if (sde.getClose() != null && sde.getClose() > 0) {
                            prices.add(sde.getClose());
                        }
                    }
                }
            } else {
                result.targetName = "Market Composite Index";
                MarketDailyMapper marketDailyMapper = session.getMapper(MarketDailyMapper.class);
                List<MarketDailyEntity> marketList = marketDailyMapper.selectAll();
                if (marketList != null) {
                    for (MarketDailyEntity mde : marketList) {
                        if (mde.getClose() != null && mde.getClose() > 0) {
                            prices.add(mde.getClose());
                        }
                    }
                }
                // Fallback to stock 1 if market_daily is empty
                if (prices.isEmpty() && stocks != null && !stocks.isEmpty()) {
                    int fallbackId = stocks.get(0).getId();
                    StockDailyMapper stockDailyMapper = session.getMapper(StockDailyMapper.class);
                    List<StockDailyEntity> dailyList = stockDailyMapper.selectByStockId(fallbackId);
                    result.targetName = stocks.get(0).getStockCode() + " (Fallback Stock)";
                    if (dailyList != null) {
                        for (StockDailyEntity sde : dailyList) {
                            if (sde.getClose() != null && sde.getClose() > 0) {
                                prices.add(sde.getClose());
                            }
                        }
                    }
                }
            }

            result.totalDays = prices.size();
            int n = prices.size() - 1;
            result.sampleSize = Math.max(0, n);

            if (n < 5) {
                // Insufficient data
                FatTailsResult ft = new FatTailsResult();
                ft.verdict = "Insufficient observations (" + prices.size() + " trading days, at least 6 required).";
                ft.qqPlotPoints = Collections.emptyList();
                ft.histogramBins = Collections.emptyList();
                ft.normalCurve = Collections.emptyList();
                result.fatTails = ft;

                VolatilityClusteringResult vc = new VolatilityClusteringResult();
                vc.verdict = "Insufficient observations (" + prices.size() + " trading days, at least 6 required).";
                vc.lags = Collections.emptyList();
                vc.rawReturnsAcf = Collections.emptyList();
                vc.absReturnsAcf = Collections.emptyList();
                vc.squaredReturnsAcf = Collections.emptyList();
                result.volatilityClustering = vc;
                return result;
            }

            // 1. Calculate log returns: R_t = ln(P_t / P_{t-1})
            double[] returns = new double[n];
            for (int i = 0; i < n; i++) {
                double pPrev = prices.get(i);
                double pCurr = prices.get(i + 1);
                returns[i] = Math.log(pCurr / pPrev);
            }

            // 2. Fat Tails analysis
            result.fatTails = computeFatTails(returns);

            // 3. Volatility Clustering analysis
            result.volatilityClustering = computeVolatilityClustering(returns);

            return result;
        }
    }

    private FatTailsResult computeFatTails(double[] returns) {
        FatTailsResult res = new FatTailsResult();
        int n = returns.length;

        // Mean & Std Dev
        double sum = 0.0;
        for (double r : returns) sum += r;
        double mean = sum / n;

        double sumSq = 0.0;
        double sumCube = 0.0;
        double sumQuad = 0.0;
        for (double r : returns) {
            double diff = r - mean;
            double diff2 = diff * diff;
            sumSq += diff2;
            sumCube += diff2 * diff;
            sumQuad += diff2 * diff2;
        }

        double variance = sumSq / n;
        double std = Math.sqrt(Math.max(1e-12, variance));
        double skewness = (sumCube / n) / Math.pow(std, 3);
        double kurtosis = (sumQuad / n) / Math.pow(std, 4);
        double excessKurtosis = kurtosis - 3.0;

        // Jarque-Bera Test
        double jbStat = (n / 6.0) * (skewness * skewness + (excessKurtosis * excessKurtosis) / 4.0);
        // p-value from Chi-square with df = 2: P(X >= jbStat) = exp(-jbStat / 2.0)
        double jbPValue = Math.exp(-jbStat / 2.0);

        res.mean = mean;
        res.stdDev = std;
        res.skewness = skewness;
        res.excessKurtosis = excessKurtosis;
        res.jbStat = jbStat;
        res.jbPValue = jbPValue;
        res.isFatTail = (excessKurtosis > 0.05 && jbPValue < 0.05);

        if (res.isFatTail) {
            res.verdict = String.format(Locale.US,
                    "Stylized Fact Confirmed: Significant fat tails detected (Excess Kurtosis = %.4f > 0, JB test p = %.4e < 0.05).",
                    excessKurtosis, jbPValue);
        } else if (excessKurtosis > 0) {
            res.verdict = String.format(Locale.US,
                    "Weak fat tail tendency (Excess Kurtosis = %.4f > 0, but JB p = %.4f >= 0.05).",
                    excessKurtosis, jbPValue);
        } else {
            res.verdict = String.format(Locale.US,
                    "No fat tails detected (Excess Kurtosis = %.4f <= 0, distribution is normal or platykurtic).",
                    excessKurtosis);
        }

        // Q-Q Plot
        double[] sorted = returns.clone();
        Arrays.sort(sorted);
        List<Double[]> qqPoints = new ArrayList<>();
        double minTheory = Double.POSITIVE_INFINITY;
        double maxTheory = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double p = (i + 0.5) / n;
            double theoretical = probit(p);
            double sampleStd = (sorted[i] - mean) / std;
            qqPoints.add(new Double[]{theoretical, sampleStd});
            minTheory = Math.min(minTheory, theoretical);
            maxTheory = Math.max(maxTheory, theoretical);
        }
        res.qqPlotPoints = qqPoints;
        res.qqReferenceLine = new Double[]{minTheory, maxTheory};

        // Density Histogram vs Fitted Normal
        double minVal = sorted[0];
        double maxVal = sorted[n - 1];
        if (Math.abs(maxVal - minVal) < 1e-6) {
            maxVal = minVal + 0.01;
        }

        int binCount = Math.min(50, Math.max(15, n / 10));
        double binWidth = (maxVal - minVal) / binCount;
        int[] counts = new int[binCount];

        for (double r : returns) {
            int binIdx = (int) ((r - minVal) / binWidth);
            if (binIdx >= binCount) binIdx = binCount - 1;
            if (binIdx < 0) binIdx = 0;
            counts[binIdx]++;
        }

        List<HistogramBin> bins = new ArrayList<>();
        for (int i = 0; i < binCount; i++) {
            double bStart = minVal + i * binWidth;
            double bEnd = bStart + binWidth;
            double bCenter = bStart + 0.5 * binWidth;
            double density = counts[i] / ((double) n * binWidth);
            double normPdf = (1.0 / (std * Math.sqrt(2.0 * Math.PI))) *
                    Math.exp(-0.5 * Math.pow((bCenter - mean) / std, 2));
            bins.add(new HistogramBin(bCenter, bStart, bEnd, density, normPdf, counts[i]));
        }
        res.histogramBins = bins;

        // Normal PDF Curve
        List<Double[]> normCurve = new ArrayList<>();
        int curvePoints = 80;
        double step = (maxVal - minVal) / (curvePoints - 1);
        for (int i = 0; i < curvePoints; i++) {
            double x = minVal + i * step;
            double pdf = (1.0 / (std * Math.sqrt(2.0 * Math.PI))) *
                    Math.exp(-0.5 * Math.pow((x - mean) / std, 2));
            normCurve.add(new Double[]{x, pdf});
        }
        res.normalCurve = normCurve;

        // Tail CCDF Log-Log
        double[] absRet = new double[n];
        for (int i = 0; i < n; i++) absRet[i] = Math.abs(returns[i]);
        Arrays.sort(absRet);
        List<Double[]> ccdfList = new ArrayList<>();
        int ccdfSamples = Math.min(60, n);
        int sampleStep = Math.max(1, n / ccdfSamples);
        for (int i = 0; i < n; i += sampleStep) {
            double x = absRet[i];
            if (x <= 1e-6) continue;
            double sampleP = (double) (n - i) / n;
            double normP = Math.max(1e-8, 2.0 * (1.0 - normalCdf(x / std)));
            ccdfList.add(new Double[]{x, sampleP, normP});
        }
        res.tailCcdfPoints = ccdfList;

        // Returns Timeline & Outliers (> 3 sigma)
        List<ReturnPoint> timeline = new ArrayList<>();
        int outlierCount = 0;
        double threshold = 3.0 * std;
        for (int i = 0; i < n; i++) {
            boolean isOut = Math.abs(returns[i] - mean) > threshold;
            if (isOut) outlierCount++;
            timeline.add(new ReturnPoint(i + 1, returns[i], isOut));
        }
        res.returnsTimeline = timeline;
        res.outlierCount = outlierCount;

        // Rolling Kurtosis & Skewness (window = 60 days)
        List<RollingStatPoint> rollList = new ArrayList<>();
        int rollWin = Math.min(60, Math.max(20, n / 10));
        for (int i = rollWin; i < n; i += 2) {
            double mSum = 0.0;
            for (int j = i - rollWin; j < i; j++) mSum += returns[j];
            double mMean = mSum / rollWin;
            double s2 = 0.0, s3 = 0.0, s4 = 0.0;
            for (int j = i - rollWin; j < i; j++) {
                double d = returns[j] - mMean;
                double d2 = d * d;
                s2 += d2;
                s3 += d2 * d;
                s4 += d2 * d2;
            }
            double mStd = Math.sqrt(Math.max(1e-12, s2 / rollWin));
            double mSkew = (s3 / rollWin) / Math.pow(mStd, 3);
            double mKurt = (s4 / rollWin) / Math.pow(mStd, 4) - 3.0;
            rollList.add(new RollingStatPoint(i + 1, mKurt, mSkew));
        }
        res.rollingStats = rollList;

        return res;
    }

    private VolatilityClusteringResult computeVolatilityClustering(double[] returns) {
        VolatilityClusteringResult res = new VolatilityClusteringResult();
        int n = returns.length;

        // Absolute & Squared returns
        double[] absReturns = new double[n];
        double[] sqReturns = new double[n];
        for (int i = 0; i < n; i++) {
            absReturns[i] = Math.abs(returns[i]);
            sqReturns[i] = returns[i] * returns[i];
        }

        // Autocorrelation function (ACF) up to lag 40
        int maxLag = Math.min(40, Math.max(2, n / 4));
        List<Integer> lags = new ArrayList<>();
        List<Double> rawAcf = new ArrayList<>();
        List<Double> absAcf = new ArrayList<>();
        List<Double> sqAcf = new ArrayList<>();

        double rawVar = computeVariance(returns);
        double absVar = computeVariance(absReturns);
        double sqVar = computeVariance(sqReturns);

        for (int k = 1; k <= maxLag; k++) {
            lags.add(k);
            rawAcf.add(computeSampleAcf(returns, k, rawVar));
            absAcf.add(computeSampleAcf(absReturns, k, absVar));
            sqAcf.add(computeSampleAcf(sqReturns, k, sqVar));
        }

        res.lags = lags;
        res.rawReturnsAcf = rawAcf;
        res.absReturnsAcf = absAcf;
        res.squaredReturnsAcf = sqAcf;
        res.upperConfidenceBand = 1.96 / Math.sqrt(n);
        res.lowerConfidenceBand = -1.96 / Math.sqrt(n);

        // Volatility timeline (daily |R_t| and 20-day rolling volatility)
        double mean = 0.0;
        for (double r : returns) mean += r;
        mean /= n;

        List<VolTimelinePoint> volTimeline = new ArrayList<>();
        int vWin = Math.min(20, Math.max(5, n / 20));
        for (int i = 0; i < n; i++) {
            int startIdx = Math.max(0, i - vWin + 1);
            int count = i - startIdx + 1;
            double sumSq = 0.0;
            for (int j = startIdx; j <= i; j++) {
                double diff = returns[j] - mean;
                sumSq += diff * diff;
            }
            double rVol = Math.sqrt(sumSq / count);
            volTimeline.add(new VolTimelinePoint(i + 1, absReturns[i], rVol));
        }
        res.volatilityTimeline = volTimeline;

        // Log-Log Volatility ACF Power-Law Decay
        List<Double[]> logAcf = new ArrayList<>();
        for (int k = 1; k <= maxLag; k++) {
            double val = absAcf.get(k - 1);
            if (val > 1e-4) {
                logAcf.add(new Double[]{Math.log10(k), Math.log10(val)});
            }
        }
        res.logAcfPoints = logAcf;

        // Engle's ARCH-LM test for conditional heteroscedasticity
        int archLags = Math.min(5, Math.max(1, n / 15));
        res.archLags = archLags;

        double[] eSq = new double[n];
        for (int i = 0; i < n; i++) {
            double err = returns[i] - mean;
            eSq[i] = err * err;
        }

        // ARCH Lag-1 Phase Space & Regression Fit
        List<Double[]> archScatter = new ArrayList<>();
        double maxESq = 0.0;
        for (int i = 1; i < n; i++) {
            archScatter.add(new Double[]{eSq[i - 1], eSq[i]});
            maxESq = Math.max(maxESq, Math.max(eSq[i - 1], eSq[i]));
        }
        res.archScatterPoints = archScatter;

        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int numPts = n - 1;
        for (int i = 1; i < n; i++) {
            sx += eSq[i - 1];
            sy += eSq[i];
            sxx += eSq[i - 1] * eSq[i - 1];
            sxy += eSq[i - 1] * eSq[i];
        }
        double denom = (double) numPts * sxx - sx * sx;
        if (Math.abs(denom) > 1e-18) {
            double slope = (numPts * sxy - sx * sy) / denom;
            double intercept = (sy - slope * sx) / numPts;
            List<Double[]> trendLine = new ArrayList<>();
            trendLine.add(new Double[]{0.0, Math.max(0, intercept)});
            trendLine.add(new Double[]{maxESq, Math.max(0, intercept + slope * maxESq)});
            res.archTrendLine = trendLine;
        }

        // Regression: e_t^2 = alpha_0 + sum_{j=1}^p alpha_j * e_{t-j}^2 + v_t
        int m = n - archLags;
        if (m > archLags + 2) {
            double[][] x = new double[m][archLags + 1];
            double[] y = new double[m];
            double ySum = 0.0;

            for (int i = 0; i < m; i++) {
                int t = archLags + i;
                y[i] = eSq[t];
                ySum += y[i];
                x[i][0] = 1.0;
                for (int j = 1; j <= archLags; j++) {
                    x[i][j] = eSq[t - j];
                }
            }

            double yMean = ySum / m;
            double tss = 0.0;
            for (double val : y) {
                tss += Math.pow(val - yMean, 2);
            }

            double[] beta = solveOls(x, y);
            if (beta != null && tss > 1e-12) {
                double rss = 0.0;
                for (int i = 0; i < m; i++) {
                    double yHat = 0.0;
                    for (int j = 0; j <= archLags; j++) {
                        yHat += x[i][j] * beta[j];
                    }
                    rss += Math.pow(y[i] - yHat, 2);
                }
                double rSquared = Math.max(0.0, Math.min(1.0, 1.0 - (rss / tss)));
                double lmStat = m * rSquared;
                double lmPValue = chiSquareSurvival(lmStat, archLags);

                res.archLmStat = lmStat;
                res.archLmPValue = lmPValue;
                res.hasArchEffect = (lmPValue < 0.05);

                if (res.hasArchEffect) {
                    res.verdict = String.format(Locale.US,
                            "Stylized Fact Confirmed: Significant ARCH effect (LM = %.2f, p = %.4e < 0.05). Long memory and volatility clustering verified.",
                            lmStat, lmPValue);
                } else {
                    res.verdict = String.format(Locale.US,
                            "No significant ARCH effect (LM = %.2f, p = %.4f >= 0.05). Volatility clustering is not pronounced.",
                            lmStat, lmPValue);
                }
            } else {
                res.archLmStat = 0.0;
                res.archLmPValue = 1.0;
                res.hasArchEffect = false;
                res.verdict = "Could not fit ARCH regression due to singular covariance matrix.";
            }
        } else {
            res.archLmStat = 0.0;
            res.archLmPValue = 1.0;
            res.hasArchEffect = false;
            res.verdict = "Sample size too small for ARCH-LM test.";
        }

        return res;
    }

    public static double normalCdf(double z) {
        if (z < -8.0) return 0.0;
        if (z > 8.0) return 1.0;
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989422804014327 * Math.exp(-z * z / 2.0);
        double prob = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return z > 0 ? 1.0 - prob : prob;
    }

    private double computeVariance(double[] series) {
        int n = series.length;
        double sum = 0.0;
        for (double v : series) sum += v;
        double mean = sum / n;
        double sumSq = 0.0;
        for (double v : series) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.max(1e-14, sumSq / n);
    }

    private double computeSampleAcf(double[] series, int lag, double variance) {
        int n = series.length;
        if (lag >= n) return 0.0;
        double sum = 0.0;
        for (double v : series) sum += v;
        double mean = sum / n;

        double cov = 0.0;
        for (int i = lag; i < n; i++) {
            cov += (series[i] - mean) * (series[i - lag] - mean);
        }
        cov /= n;
        return cov / variance;
    }

    /**
     * OLS Normal equation solver: beta = (X^T X)^(-1) X^T y using Gaussian elimination
     */
    private double[] solveOls(double[][] x, double[] y) {
        int m = x.length;
        int p = x[0].length;

        double[][] xtx = new double[p][p];
        double[] xty = new double[p];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) {
                xty[j] += x[i][j] * y[i];
                for (int k = 0; k < p; k++) {
                    xtx[j][k] += x[i][j] * x[i][k];
                }
            }
        }

        // Gaussian elimination with partial pivoting
        for (int i = 0; i < p; i++) {
            int maxRow = i;
            for (int k = i + 1; k < p; k++) {
                if (Math.abs(xtx[k][i]) > Math.abs(xtx[maxRow][i])) {
                    maxRow = k;
                }
            }
            double[] tempRow = xtx[i];
            xtx[i] = xtx[maxRow];
            xtx[maxRow] = tempRow;
            double tempVal = xty[i];
            xty[i] = xty[maxRow];
            xty[maxRow] = tempVal;

            if (Math.abs(xtx[i][i]) < 1e-12) {
                return null; // Singular matrix
            }

            for (int k = i + 1; k < p; k++) {
                double factor = xtx[k][i] / xtx[i][i];
                xty[k] -= factor * xty[i];
                for (int j = i; j < p; j++) {
                    xtx[k][j] -= factor * xtx[i][j];
                }
            }
        }

        // Back substitution
        double[] beta = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < p; j++) {
                sum += xtx[i][j] * beta[j];
            }
            beta[i] = (xty[i] - sum) / xtx[i][i];
        }
        return beta;
    }

    /**
     * Peter J. Acklam's inverse normal CDF (probit) approximation
     * Relative error < 1.15e-9 across the whole domain.
     */
    public static double probit(double p) {
        if (p <= 0.0) return -10.0;
        if (p >= 1.0) return 10.0;

        final double[] a = {
                -3.969683028665376e+01, 2.209460984245205e+02,
                -2.759285104469687e+02, 1.383577518672690e+02,
                -3.066479806614716e+01, 2.506628277459239e+00
        };
        final double[] b = {
                -5.447609879822406e+01, 1.615858368580409e+02,
                -1.556989798598866e+02, 6.680131188771972e+01,
                -1.328068155288572e+01
        };
        final double[] c = {
                -7.784894002430293e-03, -3.223964580411365e-01,
                -2.400758277161838e+00, -2.549732539343734e+00,
                4.374664141464968e+00, 2.938163982698783e+00
        };
        final double[] d = {
                7.784695709041462e-03, 3.224671290700398e-01,
                2.445134137142996e+00, 3.754408661907416e+00
        };

        final double pLow = 0.02425;
        final double pHigh = 1.0 - pLow;

        if (p < pLow) {
            double q = Math.sqrt(-2.0 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        } else if (p > pHigh) {
            double q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        } else {
            double q = p - 0.5;
            double r = q * q;
            return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
        }
    }

    /**
     * Chi-square distribution survival function P(X >= x) with df degrees of freedom
     */
    public static double chiSquareSurvival(double x, double df) {
        if (x <= 0.0) return 1.0;
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.0;
        if (Math.abs(df - 2.0) < 1e-6) {
            return Math.exp(-x / 2.0);
        }
        return gammaQ(df / 2.0, x / 2.0);
    }

    /**
     * Regularized upper incomplete gamma function Q(a, x) = 1 - P(a, x)
     */
    private static double gammaQ(double a, double x) {
        if (x < a + 1.0) {
            return 1.0 - gammaPSeries(a, x);
        } else {
            return gammaQContinuedFraction(a, x);
        }
    }

    private static double gammaPSeries(double a, double x) {
        if (x <= 0.0) return 0.0;
        double sum = 1.0 / a;
        double term = sum;
        for (int n = 1; n < 200; n++) {
            term *= x / (a + n);
            sum += term;
            if (Math.abs(term) < Math.abs(sum) * 1e-15) break;
        }
        return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
    }

    private static double gammaQContinuedFraction(double a, double x) {
        final double tiny = 1e-30;
        double b = x + 1.0 - a;
        double c = 1.0 / tiny;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i < 200; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < tiny) d = tiny;
            c = b + an / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < 1e-15) break;
        }
        return Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }

    /**
     * Lanczos approximation for log-gamma function
     */
    private static double logGamma(double x) {
        double[] p = {
                676.5203681218851, -1259.1392167224028,
                771.32342877765313, -176.61502916214059,
                12.507343278686905, -0.138571095836524,
                9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (x < 0.5) {
            return Math.log(Math.PI / Math.sin(Math.PI * x)) - logGamma(1.0 - x);
        }
        x -= 1.0;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) {
            a += p[i] / (x + i + 1);
        }
        double t = x + p.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }
}
