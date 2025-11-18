package jp.ac.tsukuba.eclab.assetmarketsimulation;

// MASON
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

// Java
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.data.DatabaseLogger;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.RetailTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.NoiseTrader;

// 导入 Config (V4.25)
import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;

public class StockMarketSim extends SimState {

    public Bag traders = new Bag();
    public Bag stocks = new Bag();
    public Market market;
    public DatabaseLogger dbLogger;
    public ValuationService valuation;

    public int numStocks;
    public int simulationDays;

    public StockMarketSim(long seed) {
        super(seed);
        numStocks = Config.MARKET_NUM_STOCKS;
        simulationDays = Config.MARKET_SIMULATION_DAYS;
    }

    /**
     * 【【V4.26 - 修复了 IPO 日志】】
     * (V4.25 逻辑 - 保持不变)
     */
    @Override
    public void start() {
        super.start();

        traders.clear();
        stocks.clear();

        // 1. 初始化服务
        dbLogger = new DatabaseLogger(this.seed());
        valuation = new ValuationService();

        // 2. 初始化股票池
        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }

        // 3. 【【V4.21】】 创建经纪人 (Agents)

        // (V4.21/V4.25 逻辑 - 保持不变)
        double totalCapital = Config.ECONOMY_TOTAL_CAPITAL_POOL;
        int totalAgents = Config.ECONOMY_TOTAL_AGENTS;
        double instCapitalPool = totalCapital * Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO;
        double retailNoiseCapitalPool = totalCapital * (1.0 - Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO);
        int numInstitutional = (int) (totalAgents * Config.AGENT_INSTITUTIONAL_POPULATION_RATIO);
        int numRetailNoise = totalAgents - numInstitutional;
        int numRetail = (int) (numRetailNoise * Config.AGENT_RETAIL_SUB_RATIO);
        int numNoise = numRetailNoise - numRetail;
        int numTraders = numInstitutional + numRetail + numNoise;
        System.out.println("Creating " + numTraders + " agents (V4.26 Top-Down Model)...");
        System.out.printf("  Institutional: %d agents, %.2f B Capital Pool\n", numInstitutional, instCapitalPool / 1e9);
        System.out.printf("  Retail: %d agents\n", numRetail);
        System.out.printf("  Noise: %d agents\n", numNoise);
        System.out.printf("  (Retail+Noise Pool: %.2f B Capital)\n", retailNoiseCapitalPool / 1e9);

        // (V4.21 逻辑 - 保持不变)
        double instMeanCash = instCapitalPool / numInstitutional;
        double instStdDev = instMeanCash * Config.AGENT_INSTITUTIONAL_CASH_STDDEV_RATIO;
        for (int i = 0; i < numInstitutional; i++) {
            double instCash = Config.nextGaussian(instMeanCash, instStdDev, instMeanCash * 0.1, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX - Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN;
            double risk = 0.3 + (0.4 * random.nextDouble());
            traders.add(new InstitutionalTrader(i, instCash, risk, maxStocks));
        }
        double retailNoiseMeanCash = retailNoiseCapitalPool / numRetailNoise;
        double retailNoiseStdDev = retailNoiseMeanCash * Config.AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO;
        for (int i = 0; i < numRetail; i++) {
            double retailCash = Config.nextGaussian(retailNoiseMeanCash, retailNoiseStdDev, 1000.0, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_RETAIL_MAX_STOCKS_MAX - Config.AGENT_RETAIL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_RETAIL_MAX_STOCKS_MIN;
            double risk = random.nextDouble();
            traders.add(new RetailTrader(numInstitutional + i, retailCash, risk, maxStocks));
        }
        for (int i = 0; i < numNoise; i++) {
            double noiseCash = Config.nextGaussian(retailNoiseMeanCash, retailNoiseStdDev, 1000.0, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_NOISE_MAX_STOCKS_MAX - Config.AGENT_NOISE_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_NOISE_MAX_STOCKS_MIN;
            traders.add(new NoiseTrader(numInstitutional + numRetail + i, noiseCash, 0.5, maxStocks));
        }
        System.out.println("Agent creation complete.");


        // 4. 【【V4.26 恢复 V4.25 逻辑 + 修复日志】】
        System.out.println("--- Executing IPO (Subscription) Phase ---");

        List<BaseTrader> shuffledTraders = new ArrayList<>(traders);

        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = (Stock) stocks.get(i);

            Map<BaseTrader, Double> subscriptions = new HashMap<>();
            double totalDemandForStock = 0;

            Collections.shuffle(shuffledTraders, new Random(random.nextLong()));

            // --- 内部循环: 需求 (Demand) ---
            for (BaseTrader trader : shuffledTraders) {
                double desiredShares = trader.calculateIPOSubscription(stock, valuation, this);
                desiredShares = Math.floor(desiredShares / 100) * 100;
                if (desiredShares > 0) {
                    subscriptions.put(trader, desiredShares);
                    totalDemandForStock += desiredShares;
                }
            }

            double sharesAvailable = stock.liquidShares;
            double oversubscriptionRatio = (totalDemandForStock > sharesAvailable)
                    ? sharesAvailable / totalDemandForStock
                    : 1.0;

            System.out.printf("IPO: %s (Available: %.0f), Demand: %.0f, Ratio: %.4f%%%n",
                    stock.stockId, sharesAvailable, totalDemandForStock, oversubscriptionRatio * 100);

            // 【【修改 V4.26】】 记录 IPO 摘要日志
            dbLogger.logIPO(stock.stockId, stock.ipoPrice, sharesAvailable, totalDemandForStock, oversubscriptionRatio * 100);

            // --- 内部循环: 分配 (Allocation) ---
            for (Map.Entry<BaseTrader, Double> entry : subscriptions.entrySet()) {
                BaseTrader trader = entry.getKey();
                double subscribedShares = entry.getValue(); // 这是经纪人 *需求* 的数量
                double allocatedShares = 0; // 这是经纪人 *赢得* 的数量

                if (trader.portfolio.getPositions().size() < trader.maxStocks) {
                    allocatedShares = Math.floor((subscribedShares * oversubscriptionRatio) / 100) * 100;

                    if (allocatedShares > 0) {
                        // (V4.25: addIPOPosition 检查并扣除现金)
                        // (V4.25: addIPOPosition 修复了资产蒸发 Bug)
                        boolean success = trader.portfolio.addIPOPosition(stock, allocatedShares, stock.ipoPrice);
                        if (!success) {
                            allocatedShares = 0; // 现金不足，分配失败
                        }
                    }
                }

                // 【【新增 V4.26】】 记录经纪人层级的认购
                // (如果中签，allocatedShares > 0；如果未中签或失败，则为 0)
                dbLogger.logIPOSubscription(stock.stockId, trader.traderId, subscribedShares, allocatedShares);
            }
        }

        // 【【修改 V4.26】】
        dbLogger.commitIPO();

        System.out.println("--- IPO Phase complete, market trading begins ---");

        // (5, 6, 7, 8 保持 V4.25 不变)
        // 5. 创建市场
        market = new Market();
        market.setup(this);

        // 6. 设置 Logger
        dbLogger.setup(this);

        // 7. 安排调度
        for (int i = 0; i < traders.size(); i++) {
            schedule.scheduleRepeating((Steppable)traders.get(i), 1, 1.0);
        }
        schedule.scheduleRepeating(market, 2, 1.0);

        Steppable dailyLogger = new Steppable() {
            private long dayStartTime;
            public void step(SimState state) {
                long steps = state.schedule.getSteps();
                int stepsPerDay = market.STEPS_PER_DAY;
                if (steps % stepsPerDay == 0) {
                    dayStartTime = System.nanoTime();
                    System.out.println(String.format("--- Day %d Starting ---", market.getCurrentDay()));
                }
                if (steps % stepsPerDay == stepsPerDay - 1) {
                    long dayEndTime = System.nanoTime();
                    double durationMs = (dayEndTime - dayStartTime) / 1_000_000.0;
                    System.out.println(String.format("--- Day %d Finished (Took %.2f ms) ---",
                            market.getCurrentDay(), durationMs));
                }
            }
        };
        schedule.scheduleRepeating(dailyLogger, 3, 1.0);
        schedule.scheduleRepeating(dbLogger, 4, market.STEPS_PER_DAY);

        // 8. 安排模拟停止
        long totalSteps = (long) simulationDays * market.STEPS_PER_DAY;
        Steppable finisher = new Steppable() {
            public void step(SimState state) {
                System.out.println("--- Simulation finished after " + simulationDays + " days ---");
                dbLogger.step(state);
                dbLogger.close();
                state.finish();
            }
        };
        schedule.scheduleOnce(totalSteps, 5, finisher);
    }

    public static void main(String[] args) {
        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}