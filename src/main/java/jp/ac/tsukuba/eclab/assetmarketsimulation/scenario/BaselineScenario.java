package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import sim.engine.SimState;
import sim.engine.Steppable;

/**
 * 基准剧本：包含科技股繁荣、流动性释放、消费股暴雷、央行加息
 */
public class BaselineScenario implements MarketScenario {

    @Override
    public String getName() {
        return "Baseline Scenario (Tech Boom -> QE -> Crash -> Hike)";
    }

    @Override
    public void apply(StockMarketSim sim) {
        int stepsPerDay = Config.MARKET_STEPS_PER_DAY;

        System.out.println(">>> Loading Scenario: " + getName());

        // 事件 1: Day 100 - 科技股利好新闻 (情绪炒作)
        sim.schedule.scheduleOnce(100 * stepsPerDay, 10, new Steppable() {
            @Override
            public void step(SimState state) {
                System.out.println("\n=== [SCENARIO EVENT] DAY 100: TECH BOOM (Sentiment) ===");
                // 科技股估值溢价 30%
                sim.intervention.triggerSectorSentimentShock(Sector.TECH, 1.3);
            }
        });

        // 事件 2: Day 200 - 央行放水 (流动性释放)
        sim.schedule.scheduleOnce(200 * stepsPerDay, 10, new Steppable() {
            @Override
            public void step(SimState state) {
                System.out.println("\n=== [SCENARIO EVENT] DAY 200: QUANTITATIVE EASING (Liquidity) ===");
                // 每个 Agent 发 500,000 现金
                sim.intervention.injectLiquidity(500000);
            }
        });

        // 事件 3: Day 300 - 消费板块基本面暴雷 (EPS 下跌)
        sim.schedule.scheduleOnce(300 * stepsPerDay, 10, new Steppable() {
            @Override
            public void step(SimState state) {
                System.out.println("\n=== [SCENARIO EVENT] DAY 300: CONSUMER SECTOR CRASH (Fundamental) ===");
                // 消费股 EPS 永久下降 30%
                sim.intervention.triggerSectorFundamentalShock(Sector.CONSUMER, -0.30);
            }
        });

        // 事件 4: Day 400 - 央行加息 (流动性收紧 + 风险偏好下降)
        sim.schedule.scheduleOnce(400 * stepsPerDay, 10, new Steppable() {
            @Override
            public void step(SimState state) {
                System.out.println("\n=== [SCENARIO EVENT] DAY 400: RATE HIKE (Tightening) ===");
                // 抽走 10% 现金，风险容忍度降低 0.2
                sim.intervention.tightenLiquidity(0.10, 0.2);

                // 同时戳破科技股泡沫 (回归均值)
                sim.intervention.resetSentiment();
            }
        });

        // 你可以在这里继续添加 Day 500, Day 600 的事件...
    }
}