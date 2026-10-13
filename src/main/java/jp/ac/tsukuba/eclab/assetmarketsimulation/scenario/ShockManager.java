package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 市场冲击管理器 (Market Shock Manager)
 * 挂载于 Market / StockMarketSim，集中管理处于活跃期的外生冲击及其时间半衰期衰减。
 * 提供非侵入式、解耦的查询接口供估值模型与交易引擎调用。
 */
public class ShockManager {

    private final List<MarketShockEvent> activeShocks = new CopyOnWriteArrayList<>();

    public void registerShock(MarketShockEvent shock) {
        if (!activeShocks.contains(shock)) {
            activeShocks.add(shock);
        }
    }

    /**
     * 计算特定冲击在 currentDay 的衰减后有效强度
     * intensity(t) = magnitude * (0.5)^((currentDay - targetDay) / halfLife)
     */
    public double calculateCurrentIntensity(MarketShockEvent shock, int currentDay) {
        if (currentDay < shock.getTargetDay()) return 0.0;
        int elapsedDays = currentDay - shock.getTargetDay();
        int halfLife = Math.max(1, shock.getHalfLifeDays());
        double decayFactor = Math.pow(0.5, (double) elapsedDays / halfLife);
        return shock.getMagnitude() * decayFactor;
    }

    /**
     * 获取全市场或特定行业的情绪折价乘数 (应用于信念价格)
     * 默认无冲击时返回 1.0 (无折价)
     */
    public double getActiveSentimentMultiplier(int currentDay) {
        double multiplier = 1.0;
        for (MarketShockEvent shock : activeShocks) {
            if (shock.getShockType() == MarketShockEvent.ShockType.SENTIMENT_CRASH) {
                double intensity = calculateCurrentIntensity(shock, currentDay);
                if (intensity > 0.001) {
                    double discount = Math.min(0.90, intensity);
                    multiplier *= (1.0 - discount);
                }
            }
        }
        return Math.max(0.10, multiplier);
    }

    /**
     * 获取指定板块的评级下调对盈利增长率的惩罚值
     */
    public double getSectorGrowthPenalty(Sector sector, int currentDay) {
        if (sector == null) return 0.0;
        double penalty = 0.0;
        for (MarketShockEvent shock : activeShocks) {
            if (shock.getShockType() == MarketShockEvent.ShockType.RATING_DOWNGRADE) {
                String target = shock.getTargetSector();
                if ("ALL".equalsIgnoreCase(target) || sector.name().equalsIgnoreCase(target)) {
                    penalty += calculateCurrentIntensity(shock, currentDay);
                }
            }
        }
        return penalty;
    }

    /**
     * 检查当前流动性配资是否处于紧缩冻结状态
     */
    public boolean isLiquidityFrozen(int currentDay) {
        for (MarketShockEvent shock : activeShocks) {
            if (shock.getShockType() == MarketShockEvent.ShockType.LIQUIDITY_SQUEEZE) {
                double intensity = calculateCurrentIntensity(shock, currentDay);
                if (intensity >= 0.05) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取流动性紧缩带来的额外维持保证金上调比例 (如 +0.20 -> 维持保证金从 1.30 升至 1.50)
     */
    public double getExtraMarginRequirement(int currentDay) {
        double maxExtra = 0.0;
        for (MarketShockEvent shock : activeShocks) {
            if (shock.getShockType() == MarketShockEvent.ShockType.LIQUIDITY_SQUEEZE) {
                double intensity = calculateCurrentIntensity(shock, currentDay);
                if (intensity > maxExtra) {
                    maxExtra = intensity;
                }
            }
        }
        return maxExtra;
    }

    /**
     * 定向注资/平准基金救市 (解耦资产划转)
     * 从系统公共财富池 (socialWealthPool) 定向注资至机构 Agent 账户，保证资金全局闭环
     */
    public void applyCapitalInjection(StockMarketSim sim, MarketShockEvent shock) {
        if (sim == null || sim.socialWealthPool <= 0) return;

        double injectionRatio = Math.min(1.0, Math.max(0.01, shock.getMagnitude()));
        double totalInjection = sim.socialWealthPool * injectionRatio;

        if (totalInjection < 1000) return;

        List<BaseTrader> recipients = new ArrayList<>();
        for (int i = 0; i < sim.traders.size(); i++) {
            Object obj = sim.traders.get(i);
            if (obj instanceof BaseTrader bt && bt.isActive()) {
                if (shock.getTargetTraderId() >= 0) {
                    if (bt.traderId == shock.getTargetTraderId()) {
                        recipients.add(bt);
                    }
                } else if (bt instanceof InstitutionalTrader) {
                    recipients.add(bt);
                }
            }
        }

        // 若没有找到机构，向全市场活跃交易者普惠救市
        if (recipients.isEmpty()) {
            for (int i = 0; i < sim.traders.size(); i++) {
                Object obj = sim.traders.get(i);
                if (obj instanceof BaseTrader bt && bt.isActive()) {
                    recipients.add(bt);
                }
            }
        }

        if (!recipients.isEmpty()) {
            sim.socialWealthPool -= totalInjection;
            double perTrader = totalInjection / recipients.size();
            for (BaseTrader t : recipients) {
                t.portfolio.cash += perTrader;
            }
            System.out.println(String.format("[BAILOUT] Injected %.2f capital into %d agents (%.2f each). Remaining social pool: %.2f",
                    totalInjection, recipients.size(), perTrader, sim.socialWealthPool));
        }
    }

    public void clear() {
        activeShocks.clear();
    }
}
