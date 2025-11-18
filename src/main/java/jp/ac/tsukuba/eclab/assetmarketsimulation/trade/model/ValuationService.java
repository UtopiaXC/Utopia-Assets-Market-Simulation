package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config; // 【修改】导入 Config
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;

public class ValuationService {

    private final double pbMultiplier;
    private final double peMultiplier;
    private final double sectorBonusTech;
    private final double sectorBonusHealthcare;
    private final double sectorBonusConsumer;
    private final double sectorBonusFinance;
    private final double sectorBonusIndustry;

    // 【修改】构造函数不再需要 ConfigLoader
    public ValuationService() {
        // 【修改】直接从 Config 类读取静态常量
        this.pbMultiplier = Config.VALUATION_PB_MULTIPLIER;
        this.peMultiplier = Config.VALUATION_PE_MULTIPLIER;
        this.sectorBonusTech = Config.VALUATION_SECTOR_TECH;
        this.sectorBonusHealthcare = Config.VALUATION_SECTOR_HEALTHCARE;
        this.sectorBonusConsumer = Config.VALUATION_SECTOR_CONSUMER;
        this.sectorBonusFinance = Config.VALUATION_SECTOR_FINANCE;
        this.sectorBonusIndustry = Config.VALUATION_SECTOR_INDUSTRY;
    }

    public double calculateFundamentalValue(Stock stock) {

        // 【修改】使用配置的乘数 (保持不变，因为它们是 final 成员变量)
        double pbValue = stock.netAssetsPerShare * pbMultiplier;

        double peValue = 0;
        if (stock.eps > 0) {
            peValue = stock.eps * peMultiplier;
        }

        double baseValue = (peValue > 0) ? (pbValue + peValue) / 2.0 : pbValue;
        double growthBonus = 1.0 + stock.earningsGrowth;
        double sectorBonus = getSectorBonus(stock.sector);

        return baseValue * growthBonus * sectorBonus;
    }

    private double getSectorBonus(Sector sector) {
        // 【修改】使用配置的乘数 (保持不变，因为它们是 final 成员变量)
        switch (sector) {
            case TECH:       return sectorBonusTech;
            case HEALTHCARE: return sectorBonusHealthcare;
            case CONSUMER:   return sectorBonusConsumer;
            case FINANCE:    return sectorBonusFinance;
            case INDUSTRY:
            default:
                return sectorBonusIndustry;
        }
    }
}