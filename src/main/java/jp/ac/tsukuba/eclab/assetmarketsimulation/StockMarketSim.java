package jp.ac.tsukuba.eclab.assetmarketsimulation;

import jp.ac.tsukuba.eclab.assetmarketsimulation.data.SimulationDataLogger;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.LeverageService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.SocialNetwork;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.NoiseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.RetailTrader;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Core ABM simulation.
 *
 * Integrates:
 * - Market order book with PolicySlot (Price Limits, Circuit Breakers, T+N Settlement)
 * - ValuationService (3-component belief model: V_fund, V_social, V_trend)
 * - SocialNetwork (cosine similarity, Top-K neighbors, belief contagion)
 * - LeverageService (margin trading, forced liquidation, short squeeze)
 */
public class StockMarketSim extends SimState {

    public int numStocks = Config.MARKET_NUM_STOCKS;
    public int simulationDays = Config.MARKET_SIMULATION_DAYS;

    // Core components
    public Market market;
    public Bag stocks;
    public Bag traders;

    // Services
    public ValuationService valuation;
    public SocialNetwork socialNetwork;
    public LeverageService leverageService;

    // Data logger
    public SimulationDataLogger dbLogger;

    // Economy
    public double socialWealthPool;

    // Configuration
    private String simulationName;
    private int stepsPerDay = Config.MARKET_STEPS_PER_DAY;
    private int socialTopK = Config.SOCIAL_TOP_K_NEIGHBORS;
    private MarketScenario scenario;

    // Lifecycle config
    private int targetPopulation = Config.ECONOMY_TARGET_POPULATION;
    private double fomoSensitivity = Config.ECONOMY_FOMO_SENSITIVITY;
    private double baseEntryProb = Config.ECONOMY_BASE_ENTRY_PROB;

    public StockMarketSim(long seed) {
        super(seed);
    }

    public void setSimulationName(String name) { this.simulationName = name; }
    public void setStepsPerDay(int steps) { this.stepsPerDay = steps; }
    public void setSocialTopK(int topK) { this.socialTopK = topK; }
    public void setScenario(MarketScenario scenario) { this.scenario = scenario; }

    @Override
    public void start() {
        super.start();

        // 1. Create market
        market = new Market(this.stepsPerDay);

        // 2. Create stocks
        stocks = new Bag();
        for (int i = 0; i < numStocks; i++) {
            stocks.add(new Stock(i));
        }

        // 3. Create services
        valuation = new ValuationService();
        socialNetwork = new SocialNetwork(socialTopK, Config.SOCIAL_NETWORK_REBUILD_INTERVAL);
        leverageService = new LeverageService();

        // 4. Create agents
        traders = new Bag();
        createAgents();

        // 5. Initialize market
        market.setup(this);

        // 6. Social wealth pool
        double totalCapital = Config.ECONOMY_TOTAL_CAPITAL_POOL;
        socialWealthPool = totalCapital * Config.ECONOMY_SOCIAL_POOL_RATIO;

        // 7. Initialize logger
        dbLogger = new SimulationDataLogger(this.seed(), simulationName);
        dbLogger.setup(this);

        // 8. Schedule core components
        schedule.scheduleRepeating(market, 0, 1.0);     // Market runs every step
        schedule.scheduleRepeating(dbLogger, 998, stepsPerDay);  // Logger runs end of each day

        // Schedule all agents
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader bt) {
                schedule.scheduleRepeating(bt, 1, 1.0);
            }
        }

        // 9. Daily lifecycle manager (end of day)
        schedule.scheduleRepeating(0, 997, new AgentLifecycleManager());

        // 10. Social network rebuild (every N days)
        schedule.scheduleRepeating(0, 996, new SocialNetworkRebuildStep());

        // 11. Leverage daily tasks (interest & margin check)
        schedule.scheduleRepeating(0, 995, new LeverageDailyStep());

        // 12. Apply scenario
        if (scenario != null) {
            scenario.apply(this);
        }

        System.out.println("=== Simulation Started ===");
        System.out.println("Stocks: " + numStocks);
        System.out.println("Agents: " + traders.size());
        System.out.println("Days: " + simulationDays);
        System.out.println("Steps/Day: " + stepsPerDay);
        System.out.println("Policy: " + market.policySlot);
        System.out.println("==========================");
    }

    private void createAgents() {
        int totalAgents = Config.ECONOMY_TOTAL_AGENTS;
        double totalCapitalPool = Config.ECONOMY_TOTAL_CAPITAL_POOL;

        // Population splits
        int numInstitutional = (int) (totalAgents * Config.AGENT_INSTITUTIONAL_POPULATION_RATIO);
        int numRetailNoise = totalAgents - numInstitutional;
        int numRetail = (int) (numRetailNoise * Config.AGENT_RETAIL_SUB_RATIO);
        int numNoise = numRetailNoise - numRetail;

        // Capital splits
        double institutionalCapitalPool = totalCapitalPool * Config.AGENT_INSTITUTIONAL_CAPITAL_RATIO;
        double retailNoiseCapitalPool = totalCapitalPool - institutionalCapitalPool;

        double avgInstitutionalCash = institutionalCapitalPool / numInstitutional;
        double avgRetailNoiseCash = retailNoiseCapitalPool / numRetailNoise;

        int traderId = 0;

        // Institutional traders
        for (int i = 0; i < numInstitutional; i++) {
            double cash = Config.nextGaussian(avgInstitutionalCash,
                    avgInstitutionalCash * Config.AGENT_INSTITUTIONAL_CASH_STDDEV_RATIO,
                    avgInstitutionalCash * 0.1, avgInstitutionalCash * 3.0);
            double riskTol = ThreadLocalRandom.current().nextDouble(0.2, 0.6);
            int maxStocks = Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN +
                    random.nextInt(Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MAX - Config.AGENT_INSTITUTIONAL_MAX_STOCKS_MIN + 1);
            InstitutionalTrader t = new InstitutionalTrader(traderId++, cash, riskTol, maxStocks);
            traders.add(t);
        }

        // Retail traders
        for (int i = 0; i < numRetail; i++) {
            double cash = Config.nextGaussian(avgRetailNoiseCash,
                    avgRetailNoiseCash * Config.AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO,
                    avgRetailNoiseCash * 0.05, avgRetailNoiseCash * 5.0);
            double riskTol = ThreadLocalRandom.current().nextDouble(0.3, 0.8);
            int maxStocks = Config.AGENT_RETAIL_MAX_STOCKS_MIN +
                    random.nextInt(Config.AGENT_RETAIL_MAX_STOCKS_MAX - Config.AGENT_RETAIL_MAX_STOCKS_MIN + 1);
            RetailTrader t = new RetailTrader(traderId++, cash, riskTol, maxStocks);
            traders.add(t);
        }

        // Noise traders
        for (int i = 0; i < numNoise; i++) {
            double cash = Config.nextGaussian(avgRetailNoiseCash,
                    avgRetailNoiseCash * Config.AGENT_RETAIL_NOISE_CASH_STDDEV_RATIO,
                    avgRetailNoiseCash * 0.05, avgRetailNoiseCash * 5.0);
            double riskTol = ThreadLocalRandom.current().nextDouble(0.5, 1.0);
            int maxStocks = Config.AGENT_NOISE_MAX_STOCKS_MIN +
                    random.nextInt(Config.AGENT_NOISE_MAX_STOCKS_MAX - Config.AGENT_NOISE_MAX_STOCKS_MIN + 1);
            NoiseTrader t = new NoiseTrader(traderId++, cash, riskTol, maxStocks);
            traders.add(t);
        }

        // Initial stock allocation (均匀分配部分资金到股票)
        for (int i = 0; i < traders.size(); i++) {
            if (!(traders.get(i) instanceof BaseTrader t)) continue;
            if (!t.isActive()) continue;

            int numToAllocate = 1 + random.nextInt(Math.min(3, t.maxStocks));
            for (int j = 0; j < numToAllocate; j++) {
                int stockIndex = random.nextInt(stocks.size());
                Stock stock = (Stock) stocks.get(stockIndex);
                double allocFraction = random.nextDouble() * 0.1 + 0.05;
                double allocCash = t.portfolio.cash * allocFraction;
                double qty = Math.floor(allocCash / stock.currentPrice / 100) * 100;
                if (qty >= 100) {
                    t.portfolio.initializePosition(stock, qty, stock.currentPrice);
                }
            }
        }
    }

    // =====================================================
    // Scheduled Step Components
    // =====================================================

    /**
     * Agent lifecycle management: bankruptcy, withdrawal, and FOMO entry
     */
    private class AgentLifecycleManager implements Steppable {
        @Override
        public void step(SimState state) {
            long currentStep = schedule.getSteps();
            if (currentStep % stepsPerDay != stepsPerDay - 1) return;

            int day = market.getCurrentDay();

            // 1. Withdrawal (savings) and bankruptcy
            for (int i = 0; i < traders.size(); i++) {
                Object obj = traders.get(i);
                if (!(obj instanceof BaseTrader t)) continue;
                if (!t.isActive()) continue;

                // Check bankruptcy
                if (t.isBankrupt()) {
                    double leftoverCash = t.portfolio.cash + t.portfolio.reservedCash;
                    double leftoverStockValue = t.portfolio.getTotalStockValue();
                    double returnedTotal = leftoverCash + leftoverStockValue + t.privateSavings;

                    // Return borrowed money
                    if (t.portfolio.borrowedCash > 0) {
                        returnedTotal -= t.portfolio.borrowedCash;
                        t.portfolio.borrowedCash = 0;
                    }

                    socialWealthPool += Math.max(0, returnedTotal);
                    t.portfolio.clear();
                    t.privateSavings = 0;
                    t.setActive(false);
                    continue;
                }

                // Check withdrawal
                double withdrawal = t.checkWithdrawal();
                if (withdrawal > 0) {
                    t.transferToSavings(withdrawal);
                    socialWealthPool += withdrawal;
                }
            }

            // 2. FOMO entry
            if (day > 10) {
                double recentReturn = market.getRecentReturn(10);
                if (recentReturn > 0) {
                    double fomoBoost = recentReturn * fomoSensitivity;
                    double entryProbability = baseEntryProb * (1.0 + fomoBoost);

                    int activeCount = 0;
                    for (int i = 0; i < traders.size(); i++) {
                        if (traders.get(i) instanceof BaseTrader bt && bt.isActive()) activeCount++;
                    }

                    if (activeCount < targetPopulation && random.nextDouble() < entryProbability) {
                        double entryCapital = Math.min(socialWealthPool * 0.001, socialWealthPool);
                        if (entryCapital > 1000) {
                            socialWealthPool -= entryCapital;
                            int newId = traders.size();
                            double riskTol = random.nextDouble() * 0.6 + 0.3;
                            int maxStocks = random.nextInt(10) + 3;

                            BaseTrader newTrader;
                            if (random.nextDouble() < 0.5) {
                                newTrader = new RetailTrader(newId, entryCapital, riskTol, maxStocks);
                            } else {
                                newTrader = new NoiseTrader(newId, entryCapital, riskTol, maxStocks);
                            }

                            traders.add(newTrader);
                            schedule.scheduleRepeating(newTrader, 1, 1.0);

                            if (dbLogger != null) {
                                dbLogger.logNewAgent(newTrader);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Social network rebuild step
     */
    private class SocialNetworkRebuildStep implements Steppable {
        @Override
        public void step(SimState state) {
            long currentStep = schedule.getSteps();
            if (currentStep % stepsPerDay != 0) return; // Only at start of day

            int day = market.getCurrentDay();
            if (socialNetwork != null) {
                socialNetwork.checkAndRebuild(day, traders);
            }
        }
    }

    /**
     * Daily leverage tasks: interest charging and margin call checks
     */
    private class LeverageDailyStep implements Steppable {
        @Override
        public void step(SimState state) {
            long currentStep = schedule.getSteps();
            if (currentStep % stepsPerDay != stepsPerDay - 2) return; // Near end of day

            if (leverageService != null) {
                // Charge daily interest
                leverageService.chargeDailyInterest(traders);

                // Check margin calls (may trigger forced liquidation → short squeeze)
                leverageService.checkMarginCalls(traders, market);
            }
        }
    }
}