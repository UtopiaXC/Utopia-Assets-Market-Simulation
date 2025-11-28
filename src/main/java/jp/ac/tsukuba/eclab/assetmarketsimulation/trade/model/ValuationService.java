package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;

import java.util.HashMap;
import java.util.Map;

public class ValuationService {

    private final double pbMultiplier;
    private final double peMultiplier;

    // 静态配置的板块加成
    private final Map<Sector, Double> baseSectorBonus;

    // 【新增】动态的情绪乘数 (默认为 1.0)
    private Map<Sector, Double> dynamicSentimentMultipliers;

    public ValuationService() {
        this.pbMultiplier = Config.VALUATION_PB_MULTIPLIER;
        this.peMultiplier = Config.VALUATION_PE_MULTIPLIER;

        this.baseSectorBonus = new HashMap<>();
        this.baseSectorBonus.put(Sector.TECH, Config.VALUATION_SECTOR_TECH);
        this.baseSectorBonus.put(Sector.HEALTHCARE, Config.VALUATION_SECTOR_HEALTHCARE);
        this.baseSectorBonus.put(Sector.CONSUMER, Config.VALUATION_SECTOR_CONSUMER);
        this.baseSectorBonus.put(Sector.FINANCE, Config.VALUATION_SECTOR_FINANCE);
        this.baseSectorBonus.put(Sector.INDUSTRY, Config.VALUATION_SECTOR_INDUSTRY);

        // 初始化动态情绪
        this.dynamicSentimentMultipliers = new HashMap<>();
        resetSectorSentiment();
    }

    // 【新增】设置动态情绪
    public void setSectorSentiment(Sector sector, double multiplier) {
        this.dynamicSentimentMultipliers.put(sector, multiplier);
    }

    public void resetSectorSentiment() {
        for (Sector s : Sector.values()) {
            this.dynamicSentimentMultipliers.put(s, 1.0);
        }
    }

    public double calculateFundamentalValue(Stock stock) {
        double pbValue = stock.netAssetsPerShare * pbMultiplier;

        double peValue = 0;
        if (stock.eps > 0) {
            peValue = stock.eps * peMultiplier;
        }

        double baseValue = (peValue > 0) ? (pbValue + peValue) / 2.0 : pbValue;
        double growthBonus = 1.0 + stock.earningsGrowth;

        // 【修改】综合奖赏 = 静态配置 * 动态情绪
        double totalSectorBonus = getBaseSectorBonus(stock.sector) * dynamicSentimentMultipliers.get(stock.sector);

        return baseValue * growthBonus * totalSectorBonus;
    }

    private double getBaseSectorBonus(Sector sector) {
        return baseSectorBonus.getOrDefault(sector, 1.0);
    }
}