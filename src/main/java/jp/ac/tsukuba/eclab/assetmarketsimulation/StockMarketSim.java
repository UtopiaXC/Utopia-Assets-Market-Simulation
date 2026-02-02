package jp.ac.tsukuba.eclab.assetmarketsimulation;

import lombok.Setter;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
    private MarketScenario activeScenario;
    public int numStocks;
    public int simulationDays;
    public double socialWealthPool;
    @Setter
    private String simulationName; // Custom simulation name
    @Setter
    private int stepsPerDay = Config.MARKET_STEPS_PER_DAY; // Steps per day, configurable

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
        dbLogger = new DatabaseLogger(this.seed(), this.simulationName);
        valuation = new ValuationService();
        intervention = new InterventionService(this);
        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }
        createAgents();

        // 初始化资金池。分配场内资金与场外资金
        // Initial assets pools of market assets and outside (social) assets
        double totalInitialCash = 0;
        for (int i = 0; i < traders.size(); i++) {
            totalInitialCash += ((BaseTrader) traders.get(i)).portfolio.cash;
        }
        this.socialWealthPool = totalInitialCash * Config.ECONOMY_SOCIAL_POOL_RATIO;
        System.out.println("Social Wealth Pool Initialized: " + this.socialWealthPool);
        distributeInitialShares();
        market = new Market(this.stepsPerDay);
        market.setup(this);
        dbLogger.setup(this);

        for (int i = 0; i < traders.size(); i++) {
            schedule.scheduleRepeating((Steppable) traders.get(i), 1, 1.0);
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

        // Agent生命周期管理器
        // Manager for lifecycle of agents
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
            int maxStocks = random
                    .nextInt(Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX - Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN + 1)
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
                    if (loopCount > 3)
                        break;
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

    // 统计活跃Agent
    // Count activated agents
    public int countActiveAgents() {
        int count = 0;
        for (int i = 0; i < traders.size(); i++) {
            if (traders.get(i) instanceof BaseTrader) {
                if (((BaseTrader) traders.get(i)).isActive())
                    count++;
            }
        }
        return count;
    }

    // Agent生命周期管理器
    // Lifecycle manager of agents
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
                if (!(obj instanceof BaseTrader))
                    continue;
                BaseTrader agent = (BaseTrader) obj;
                if (!agent.isActive())
                    continue;
                // 破产检查
                // Bankruptcy checker
                if (agent.isBankrupt()) {
                    // 清算。将股票残值、现金、储蓄 全部转回社会资金池
                    // Liquidation. All residual value of shares, cash, and savings will be
                    // transferred back to the social capital pool.
                    double stockValue = agent.portfolio.getTotalStockValue();
                    double totalValue = stockValue + agent.portfolio.cash + agent.portfolio.reservedCash
                            + agent.privateSavings;
                    socialWealthPool += totalValue;
                    // 彻底移除,清空资产，标记死亡
                    // Remove Agent. Mark as dead.
                    agent.portfolio.clear();
                    agent.privateSavings = 0;
                    agent.setActive(false);
                    // System.out.println("Agent " + agent.traderId + " went BANKRUPT. Recycled " +
                    // totalValue);
                    continue;
                }

                // 撤出本金
                // Withdraw Principal
                double withdrawn = agent.checkWithdrawal();
                if (withdrawn > 0) {
                    agent.transferToSavings(withdrawn);
                    // System.out.println("Agent " + agent.traderId + " secured savings: " +
                    // withdrawn);
                }
            }
        }

        private void manageEntries(SimState state) {
            int currentPop = countActiveAgents();
            // 如果市场Agent数少于初始总数，生成一个Agent
            // Generate agent when total count is less than initial population
            double baseProb = 0;
            if (currentPop < Config.ECONOMY_TARGET_POPULATION) {
                baseProb = Config.ECONOMY_BASE_ENTRY_PROB;
            }

            // FOMO情绪，连续上涨加入市场
            // FOMO Check, If market continue rais, join
            double marketReturn = market.getRecentReturn(30); // 过去30天涨幅
            double fomoProb = 0;
            if (marketReturn > 0) {
                fomoProb = marketReturn * Config.ECONOMY_FOMO_SENSITIVITY;
            }
            double totalEntryProb = baseProb + fomoProb;
            // TODO: DYNAMICALLY GENERATE INITIAL CAPITAL
            double avgStartCash = 100_000.0;
            if (random.nextDouble() < totalEntryProb && socialWealthPool > avgStartCash) {
                createNewRetailAgent(avgStartCash);
            }
        }

        private void createNewRetailAgent(double avgCash) {
            double initialCash = Config.nextGaussian(avgCash, avgCash * 0.2, 10000, avgCash * 2);
            if (initialCash > socialWealthPool)
                initialCash = socialWealthPool;
            socialWealthPool -= initialCash;
            int newId = traders.size();
            double risk = random.nextDouble();
            int maxStocks = random.nextInt(Config.AGENT_RETAIL_MAX_STOCKS_MAX - Config.AGENT_RETAIL_MAX_STOCKS_MIN + 1)
                    + Config.AGENT_RETAIL_MAX_STOCKS_MIN;
            RetailTrader newAgent = new RetailTrader(newId, initialCash, risk, maxStocks);
            traders.add(newAgent);
            schedule.scheduleRepeating(newAgent, 1, 1.0);
        }
    }

    public static void main(String[] args) {
        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}