package jp.ac.tsukuba.eclab.assetmarketsimulation;

// MASON
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.data.DatabaseLogger;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
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

    public DatabaseLogger dbLogger;

    public ValuationService valuation;
    public InterventionService intervention;

    // 当前激活的剧本
    private MarketScenario activeScenario;

    public int numStocks;
    public int simulationDays;

    // 【新增 V4.33】 社会公共资金池 (Social Wealth Pool)
    public double socialWealthPool;

    public StockMarketSim(long seed) {
        super(seed);
        numStocks = Config.MARKET_NUM_STOCKS;
        simulationDays = Config.MARKET_SIMULATION_DAYS;
        this.activeScenario = new BaselineScenario();
    }

    public void setScenario(MarketScenario scenario) {
        this.activeScenario = scenario;
    }

    @Override
    public void start() {
        super.start();

        traders.clear();
        stocks.clear();

        if (dbLogger != null) {
            dbLogger.close();
            dbLogger = null;
        }
        dbLogger = new DatabaseLogger(this.seed());

        valuation = new ValuationService();
        intervention = new InterventionService(this);

        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }

        createAgents();

        // 【新增 V4.33】 初始化社会公共资金池
        // 统计所有场内初始资金，按照倍数设置场外池
        double totalInitialCash = 0;
        for(int i=0; i<traders.size(); i++) {
            totalInitialCash += ((BaseTrader)traders.get(i)).portfolio.cash;
        }
        this.socialWealthPool = totalInitialCash * Config.ECONOMY_SOCIAL_POOL_RATIO;
        System.out.println("Social Wealth Pool Initialized: " + this.socialWealthPool);

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
                    System.out.println(String.format("--- Day %d Starting [Agents: %d, Pool: %.2e] ---",
                            market.getCurrentDay(), countActiveAgents(), socialWealthPool));
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

        // 【新增 V4.33】 调度生命周期管理器 (每天结束前执行)
        schedule.scheduleRepeating(new AgentLifecycleManager(), 5, market.STEPS_PER_DAY);

        long totalSteps = (long) simulationDays * market.STEPS_PER_DAY;
        Steppable finisher = new Steppable() {
            public void step(SimState state) {
                System.out.println("--- Simulation finished after " + simulationDays + " days ---");
                dbLogger.step(state);
                dbLogger.close();
                dbLogger = null;
                state.finish();
            }
        };
        schedule.scheduleOnce(totalSteps, 6, finisher);

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

            Collections.shuffle(agentPool, new Random(this.seed() + i));
            double batchSize = Math.max(100, Math.floor(stock.liquidShares * 0.005));
            batchSize = Math.floor(batchSize / 100) * 100;

            int agentIndex = 0;
            int loopCount = 0;

            while (remainingShares > 0) {
                if (agentIndex >= agentPool.size()) {
                    agentIndex = 0;
                    loopCount++;
                    if (loopCount > 3) break;
                }

                BaseTrader agent = agentPool.get(agentIndex);
                agentIndex++;

                boolean hasStock = agent.portfolio.getPositions().containsKey(stock);
                if (!hasStock && agent.portfolio.getPositions().size() >= agent.maxStocks) {
                    continue;
                }

                double currentAssets = agent.portfolio.getTotalAssets();
                double allocationCost = batchSize * price;

                if (agent.portfolio.cash < allocationCost ||
                        (agent.portfolio.cash - allocationCost) < (currentAssets * 0.20)) {
                    continue;
                }

                double allocateQty = Math.min(batchSize, remainingShares);
                boolean success = agent.portfolio.initializePosition(stock, allocateQty, price);

                if (success) {
                    remainingShares -= allocateQty;
                }
            }
        }
        System.out.println("--- Initial Distribution Complete ---");
    }

    // 统计活跃 Agent
    public int countActiveAgents() {
        int count = 0;
        for(int i=0; i<traders.size(); i++) {
            if (traders.get(i) instanceof BaseTrader) {
                if (((BaseTrader)traders.get(i)).isActive()) count++;
            }
        }
        return count;
    }

    // 【新增 V4.33】 内部类：Agent 生命周期管理器
    class AgentLifecycleManager implements Steppable {
        @Override
        public void step(SimState state) {
            manageExits(state);
            manageEntries(state);
        }

        private void manageExits(SimState state) {
            Iterator<Object> iter = traders.iterator();
            while (iter.hasNext()) {
                Object obj = iter.next();
                if (!(obj instanceof BaseTrader)) continue;
                BaseTrader agent = (BaseTrader) obj;

                if (!agent.isActive()) continue;

                // 1. 破产检查 (绝望离场)
                if (agent.isBankrupt()) {
                    // 清算：将股票残值 (简化为现价) + 现金 + 储蓄 全部转回社会资金池
                    double stockValue = agent.portfolio.getTotalStockValue();
                    double totalValue = stockValue + agent.portfolio.cash + agent.portfolio.reservedCash + agent.privateSavings;

                    socialWealthPool += totalValue;

                    // 彻底移除 (清空资产，标记死亡)
                    agent.portfolio.clear();
                    agent.privateSavings = 0;
                    agent.setActive(false);
                    // System.out.println("Agent " + agent.traderId + " went BANKRUPT. Recycled " + totalValue);
                    continue;
                }

                // 2. 撤资检查 (止盈/保本)
                double withdrawn = agent.checkWithdrawal();
                if (withdrawn > 0) {
                    agent.transferToSavings(withdrawn);
                    // System.out.println("Agent " + agent.traderId + " secured savings: " + withdrawn);
                }
            }
        }

        private void manageEntries(SimState state) {
            int currentPop = countActiveAgents();

            // A. 基础补充概率 (缺人就补)
            double baseProb = 0;
            if (currentPop < Config.ECONOMY_TARGET_POPULATION) {
                baseProb = Config.ECONOMY_BASE_ENTRY_PROB;
            }

            // B. FOMO 情绪概率 (涨了就追)
            double marketReturn = market.getRecentReturn(30); // 过去30天涨幅
            double fomoProb = 0;
            if (marketReturn > 0) {
                fomoProb = marketReturn * Config.ECONOMY_FOMO_SENSITIVITY;
            }

            double totalEntryProb = baseProb + fomoProb;

            // 尝试生成新 Agent
            // 限制：资金池里必须有钱 (至少够一个标准的初始资金)
            // 这里取一个近似值，例如 10万
            double avgStartCash = 100_000.0;

            if (random.nextDouble() < totalEntryProb && socialWealthPool > avgStartCash) {
                createNewRetailAgent(avgStartCash);
            }
        }

        private void createNewRetailAgent(double avgCash) {
            // 生成初始资金
            double initialCash = Config.nextGaussian(avgCash, avgCash * 0.2, 10000, avgCash * 2);
            if (initialCash > socialWealthPool) initialCash = socialWealthPool;

            // 扣除社会池
            socialWealthPool -= initialCash;

            // 创建新 ID (简单递增)
            int newId = traders.size();

            // 创建 RetailTrader
            // 参数：riskTolerance 随机，MaxStocks 随机
            double risk = random.nextDouble();
            int maxStocks = random.nextInt(Config.AGENT_RETAIL_MAX_STOCKS_MAX - Config.AGENT_RETAIL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_RETAIL_MAX_STOCKS_MIN;

            RetailTrader newAgent = new RetailTrader(newId, initialCash, risk, maxStocks);

            // 加入 Bag 并调度
            traders.add(newAgent);
            schedule.scheduleRepeating(newAgent, 1, 1.0);

            // System.out.println("New Agent " + newId + " entered the market. Pool left: " + socialWealthPool);
        }
    }

    public static void main(String[] args) {
        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}