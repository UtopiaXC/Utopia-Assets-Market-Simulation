package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;

/**
 * 市场剧本接口
 * 实现此接口来定义一系列宏观干预事件
 */
public interface MarketScenario {

    /**
     * 获取剧本名称
     */
    String getName();

    /**
     * 将剧本应用到模拟器中
     * 在这里使用 sim.schedule.scheduleOnce() 来安排未来的事件
     * @param sim 模拟器实例
     */
    void apply(StockMarketSim sim);
}