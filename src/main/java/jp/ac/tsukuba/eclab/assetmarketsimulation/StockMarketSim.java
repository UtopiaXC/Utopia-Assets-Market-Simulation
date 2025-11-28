package jp.ac.tsukuba.eclab.assetmarketsimulation;

// MASON
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
// 注意：请确保 InterventionService 的包路径与实际文件位置一致
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.InterventionService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.BaselineScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.RetailTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.NoiseTrader;

public class StockMarketSim extends SimState {

    public Bag traders = new Bag();
    public Bag stocks = new Bag();
    public Market market;

    // 【关键修复】 这里千万不要赋值！不要写 = new DatabaseLogger(...)
    // 保持为 null，直到 start() 被调用
    public DatabaseLogger dbLogger;

    public ValuationService valuation;
    public InterventionService intervention;

    // 当前激活的剧本
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
     * 设置要运行的剧本
     */
    public void setScenario(MarketScenario scenario) {
        this.activeScenario = scenario;
    }

    @Override
    public void start() {
        super.start();

        // 清理旧数据
        traders.clear();
        stocks.clear();

        // 【关键修复】 数据库初始化逻辑
        // 1. 如果存在旧的 Logger (例如 UI 界面点击了 Stop 后又点击 Start)，先关闭它
        if (dbLogger != null) {
            dbLogger.close();
            dbLogger = null;
        }
        // 2. 只有在模拟真正开始时，才创建新的数据库文件
        dbLogger = new DatabaseLogger(this.seed());

        // 初始化服务
        valuation = new ValuationService();
        intervention = new InterventionService(this);

        // 初始化股票
        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }

        // 创建 Agent
        createAgents();

        // 初始分配 (替代 IPO)
        distributeInitialShares();

        // 创建市场
        market = new Market();
        market.setup(this);

        // 设置 Logger (准备 Statement)
        dbLogger.setup(this);

        // 安排调度: Traders
        for (int i = 0; i < traders.size(); i++) {
            schedule.scheduleRepeating((Steppable)traders.get(i), 1, 1.0);
        }
        // 安排调度: Market
        schedule.scheduleRepeating(market, 2, 1.0);

        // 安排调度: Daily Console Logger
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

        // 安排调度: Database Logger (每天记录一次)
        schedule.scheduleRepeating(dbLogger, 4, market.STEPS_PER_DAY);

        // 安排模拟停止
        long totalSteps = (long) simulationDays * market.STEPS_PER_DAY;
        Steppable finisher = new Steppable() {
            public void step(SimState state) {
                System.out.println("--- Simulation finished after " + simulationDays + " days ---");
                dbLogger.step(state); // 记录最后一步
                dbLogger.close();     // 关闭连接
                dbLogger = null;      // 置空
                state.finish();
            }
        };
        schedule.scheduleOnce(totalSteps, 5, finisher);

        // ==========================================
        // 应用选定的剧本
        // ==========================================
        if (this.activeScenario != null) {
            System.out.println("Applying Scenario: " + this.activeScenario.getName());
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

            // 每批分配 0.5%
            double batchSize = Math.max(100, Math.floor(stock.liquidShares * 0.005));
            batchSize = Math.floor(batchSize / 100) * 100;

            int agentIndex = 0;
            int loopCount = 0;

            while (remainingShares > 0) {
                if (agentIndex >= agentPool.size()) {
                    agentIndex = 0;
                    loopCount++;
                    // 防止死循环：如果循环3次还没分完，说明大家都没钱了
                    if (loopCount > 3) {
                        System.err.println("Warning: Could not distribute all shares for " + stock.stockId + ". Agents ran out of cash/slots.");
                        break;
                    }
                }

                BaseTrader agent = agentPool.get(agentIndex);
                agentIndex++;

                // 1. 持仓上限检查
                boolean hasStock = agent.portfolio.getPositions().containsKey(stock);
                if (!hasStock && agent.portfolio.getPositions().size() >= agent.maxStocks) {
                    continue;
                }

                // 2. 现金缓冲检查 (Cash Buffer)
                // 只有当分配后剩余现金 > 总资产的 20% 时，才允许分配。
                double currentAssets = agent.portfolio.getTotalAssets();
                double allocationCost = batchSize * price;

                if (agent.portfolio.cash < allocationCost ||
                        (agent.portfolio.cash - allocationCost) < (currentAssets * 0.20)) {
                    continue;
                }

                double allocateQty = Math.min(batchSize, remainingShares);

                // 执行分配 (initializePosition 内部会扣钱)
                boolean success = agent.portfolio.initializePosition(stock, allocateQty, price);

                if (success) {
                    remainingShares -= allocateQty;
                } else {
                    // 如果标准包买不起，尝试买能买得起的部分 (且符合现金缓冲)
                    // 这里简化逻辑：如果因为现金不足失败，我们就不强行塞小额了，直接跳过，
                    // 留给下一个更有钱的 Agent
                }
            }
        }
        System.out.println("--- Initial Distribution Complete ---");
    }

    public static void main(String[] args) {
        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}