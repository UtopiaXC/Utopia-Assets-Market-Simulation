package jp.ac.tsukuba.eclab.assetmarketsimulation;

// MASON
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.InterventionService;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.data.DatabaseLogger;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketScenario; // 导入接口
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.BaselineScenario; // 导入默认实现
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.RetailTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.NoiseTrader;

public class StockMarketSim extends SimState {

    public Bag traders = new Bag();
    public Bag stocks = new Bag();
    public Market market;
    public DatabaseLogger dbLogger;
    public ValuationService valuation;
    public InterventionService intervention;

    // 【新增 V4.33】 当前激活的剧本
    private MarketScenario activeScenario;

    public int numStocks;
    public int simulationDays;

    public StockMarketSim(long seed) {
        super(seed);
        numStocks = Config.MARKET_NUM_STOCKS;
        simulationDays = Config.MARKET_SIMULATION_DAYS;

        // 默认加载基准剧本，防止空指针
        this.activeScenario = new BaselineScenario();
    }

    /**
     * 【新增 V4.33】 设置要运行的剧本
     * 可以在 main 方法中调用此方法来切换不同的实验场景
     */
    public void setScenario(MarketScenario scenario) {
        this.activeScenario = scenario;
    }

    @Override
    public void start() {
        super.start();

        traders.clear();
        stocks.clear();

        dbLogger = new DatabaseLogger(this.seed());
        valuation = new ValuationService();
        intervention = new InterventionService(this);

        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }

        createAgents();
        distributeInitialShares();

        market = new Market();
        market.setup(this);
        dbLogger.setup(this);

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

        // ==========================================
        // 【修改 V4.33】 应用选定的剧本
        // ==========================================
        if (this.activeScenario != null) {
            this.activeScenario.apply(this);
        }
    }

    private void createAgents() {
        double totalCapital = Config.ECONOMY_TOTAL_CAPITAL_POOL;
        int totalAgents = Config.ECONOMY_TOTAL_AGENTS;
        double instCapitalPool = totalCapital * Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO;
        double retailNoiseCapitalPool = totalCapital * (1.0 - Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO);

        int numInstitutional = (int) (totalAgents * Config.AGENT_INSTITUTIONAL_POPULATION_RATIO);
        int numRetailNoise = totalAgents - numInstitutional;
        int numRetail = (int) (numRetailNoise * Config.AGENT_RETAIL_SUB_RATIO);
        int numNoise = numRetailNoise - numRetail;

        System.out.println("Creating " + (numInstitutional + numRetail + numNoise) + " agents...");

        // Institutional
        double instMeanCash = instCapitalPool / numInstitutional;
        double instStdDev = instMeanCash * Config.AGENT_INSTITUTIONAL_CASH_STDDEV_RATIO;
        for (int i = 0; i < numInstitutional; i++) {
            double instCash = Config.nextGaussian(instMeanCash, instStdDev, instMeanCash * 0.1, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX - Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN;
            double risk = 0.3 + (0.4 * random.nextDouble());
            traders.add(new InstitutionalTrader(i, instCash, risk, maxStocks));
        }

        // Retail
        double retailNoiseMeanCash = retailNoiseCapitalPool / numRetailNoise;
        double retailNoiseStdDev = retailNoiseMeanCash * Config.AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO;
        for (int i = 0; i < numRetail; i++) {
            double retailCash = Config.nextGaussian(retailNoiseMeanCash, retailNoiseStdDev, 1000.0, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_RETAIL_MAX_STOCKS_MAX - Config.AGENT_RETAIL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_RETAIL_MAX_STOCKS_MIN;
            double risk = random.nextDouble();
            traders.add(new RetailTrader(numInstitutional + i, retailCash, risk, maxStocks));
        }

        // Noise
        for (int i = 0; i < numNoise; i++) {
            double noiseCash = Config.nextGaussian(retailNoiseMeanCash, retailNoiseStdDev, 1000.0, Double.MAX_VALUE);
            int maxStocks = random.nextInt(Config.AGENT_NOISE_MAX_STOCKS_MAX - Config.AGENT_NOISE_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_NOISE_MAX_STOCKS_MIN;
            traders.add(new NoiseTrader(numInstitutional + numRetail + i, noiseCash, 0.5, maxStocks));
        }
    }

    private void distributeInitialShares() {
        System.out.println("--- Distributing Initial Shares (Forced Allocation) ---");

        List<BaseTrader> agentPool = new ArrayList<>();
        for (int i = 0; i < traders.size(); i++) {
            agentPool.add((BaseTrader) traders.get(i));
        }

        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = (Stock) stocks.get(i);
            double remainingShares = stock.liquidShares;
            double price = stock.ipoPrice;

            System.out.printf("Allocating %s (Total: %.0f, Price: %.2f)...%n", stock.stockId, remainingShares, price);

            Collections.shuffle(agentPool, new Random(this.seed() + i));

            double batchSize = Math.max(100, Math.floor(stock.liquidShares * 0.005));
            batchSize = Math.floor(batchSize / 100) * 100;

            int agentIndex = 0;
            int loopCount = 0;

            while (remainingShares > 0) {
                if (agentIndex >= agentPool.size()) {
                    agentIndex = 0;
                    loopCount++;
                    if (loopCount > 3) {
                        System.err.println("Warning: Could not distribute all shares for " + stock.stockId + ". Agents ran out of cash/slots.");
                        break;
                    }
                }

                BaseTrader agent = agentPool.get(agentIndex);
                agentIndex++;

                boolean hasStock = agent.portfolio.getPositions().containsKey(stock);
                if (!hasStock && agent.portfolio.getPositions().size() >= agent.maxStocks) {
                    continue;
                }

                double allocateQty = Math.min(batchSize, remainingShares);
                boolean success = agent.portfolio.initializePosition(stock, allocateQty, price);

                if (success) {
                    remainingShares -= allocateQty;
                } else {
                    double maxAffordable = Math.floor((agent.portfolio.cash / price) / 100) * 100;
                    if (maxAffordable > 0 && maxAffordable < allocateQty) {
                        allocateQty = Math.min(maxAffordable, remainingShares);
                        if (agent.portfolio.initializePosition(stock, allocateQty, price)) {
                            remainingShares -= allocateQty;
                        }
                    }
                }
            }
        }
        System.out.println("--- Initial Distribution Complete ---");
    }

    public static void main(String[] args) {
        // 在这里，你可以通过修改代码来切换不同的剧本
        // 例如:
         StockMarketSim sim = new StockMarketSim(System.currentTimeMillis());
         sim.setScenario(new BaselineScenario());
         sim.start();

        // MASON 的标准启动方式 (doLoop) 会自动调用构造函数
        // 如果你需要通过命令行参数控制剧本，可以在这里解析 args

        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}