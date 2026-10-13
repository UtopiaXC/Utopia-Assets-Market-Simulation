package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.PolicySlot;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.PolicyEvent;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.SimulationEvent;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketShockEvent;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.ScenarioRegistry;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import org.springframework.stereotype.Service;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.*;
import java.util.concurrent.*;

/**
 * Core simulation control service
 * Manages simulation lifecycle, speed, and policy slot modifications
 */
@Service
public class SimulationService {

    private SimulationSession currentSession;
    private final Object sessionLock = new Object();

    // Status listeners (for WebSocket)
    private final List<StatusListener> statusListeners = new CopyOnWriteArrayList<>();

    // Scheduled simulation events (Policy and Market Shock)
    private final List<SimulationEvent> scheduledEvents = new CopyOnWriteArrayList<>();

    public interface StatusListener {
        void onStatusUpdate(SimulationSession.SessionStatus status);
    }

    // =====================================================
    // Session Management
    // =====================================================

    public SimulationSession getCurrentSession() {
        return currentSession;
    }

    public SimulationSession.SessionStatus getStatus() {
        if (currentSession == null) {
            return new SimulationSession.SessionStatus(
                    null, null, "NO_SESSION", 0, 0, 0, 0, 0, 1.0, null, 0, 0);
        }
        return currentSession.toStatus();
    }

    /**
     * Create and start a new simulation
     */
    public SimulationSession.SessionStatus startSimulation(SimulationConfig config) {
        synchronized (sessionLock) {
            if (currentSession != null && currentSession.isRunning()) {
                throw new IllegalStateException("A simulation is already running. Stop it first.");
            }

            // Create new session
            currentSession = new SimulationSession(config);

            // Create and configure simulation
            StockMarketSim sim = new StockMarketSim(currentSession.getSeed());
            sim.numStocks = config.getNumStocks();
            sim.simulationDays = config.getSimulationDays();
            sim.setSimulationName(config.getSimulationName());
            sim.setStepsPerDay(config.getStepsPerDay());
            sim.setSocialTopK(config.getSocialTopK());
            
            // Apply scenario
            MarketScenario scenario = ScenarioRegistry.getScenario(config.getScenarioName());
            sim.setScenario(scenario);
            sim.setSimulationConfig(config);

            // Register pre-scheduled policy and shock events
            int stepsPerDay = config.getStepsPerDay();
            for (SimulationEvent event : scheduledEvents) {
                if (!event.isExecuted()) {
                    long targetStep = (long) event.getTargetDay() * stepsPerDay;
                    sim.schedule.scheduleOnce(targetStep, 0, event);
                }
            }

            currentSession.setSimulation(sim);
            currentSession.setBaseStepDelayMs((int) config.getStepDelay());

            // Start in background thread
            Thread simThread = new Thread(() -> runSimulation(), "SimulationThread-" + currentSession.getSessionId());
            currentSession.setSimulationThread(simThread);
            currentSession.transitionTo(SimulationSession.State.RUNNING);
            simThread.start();

            return currentSession.toStatus();
        }
    }

    private void runSimulation() {
        StockMarketSim sim = currentSession.getSimulation();
        SimulationConfig config = currentSession.getConfig();

        try {
            sim.start();

            // Apply initial policy slot from config
            PolicySlot slot = sim.market.policySlot;
            slot.setPriceLimitRatio(config.getPriceLimitRatio());
            slot.setCircuitBreakerThreshold(config.getCircuitBreakerThreshold());
            slot.setMaxLeverageRatio(config.getMaxLeverageRatio());
            slot.setSettlementDays(config.getSettlementDays());

            int totalSteps = config.getSimulationDays() * config.getStepsPerDay();
            long lastNotifyTime = 0;

            while (sim.schedule.getSteps() < totalSteps) {
                // Check pause state
                while (currentSession.isPaused()) {
                    Thread.sleep(100);
                }

                // Check if should stop
                if (!currentSession.isRunning() && !currentSession.isPaused()) {
                    break;
                }

                // Execute one step
                if (!sim.schedule.step(sim)) {
                    break;
                }

                // Update progress
                int day = sim.market != null ? sim.market.getCurrentDay() : 0;
                int activeAgents = countActiveAgents(sim);
                double index = sim.market != null ? sim.market.marketIndex : 0;

                currentSession.updateProgress(day, sim.schedule.getSteps(), activeAgents, index);

                // Notify listeners (Rate limited to avoid flooding frontend)
                long now = System.currentTimeMillis();
                if (now - lastNotifyTime > 50 || sim.schedule.getSteps() >= totalSteps) {
                    notifyStatusUpdate();
                    lastNotifyTime = now;
                }

                // Speed control delay
                int delay = currentSession.getEffectiveDelay();
                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }

            // Simulation complete
            if (sim.dbLogger != null) {
                sim.dbLogger.close();
            }

            currentSession.transitionTo(SimulationSession.State.COMPLETED);

        } catch (InterruptedException e) {
            currentSession.setError("Simulation interrupted");
        } catch (Exception e) {
            currentSession.setError(e.getMessage());
            e.printStackTrace();
        } finally {
            notifyStatusUpdate();
        }
    }

    private int countActiveAgents(StockMarketSim sim) {
        int count = 0;
        for (int i = 0; i < sim.traders.size(); i++) {
            Object obj = sim.traders.get(i);
            if (obj instanceof BaseTrader && ((BaseTrader) obj).isActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Pause the simulation
     */
    public SimulationSession.SessionStatus pause() {
        if (currentSession != null && currentSession.isRunning()) {
            currentSession.transitionTo(SimulationSession.State.PAUSED);
        }
        return getStatus();
    }

    /**
     * Resume a paused simulation
     */
    public SimulationSession.SessionStatus resume() {
        if (currentSession != null && currentSession.isPaused()) {
            currentSession.transitionTo(SimulationSession.State.RUNNING);
        }
        return getStatus();
    }

    /**
     * Stop the simulation
     */
    public SimulationSession.SessionStatus stop() {
        if (currentSession != null) {
            currentSession.interrupt();
            currentSession.transitionTo(SimulationSession.State.COMPLETED);

            StockMarketSim sim = currentSession.getSimulation();
            if (sim != null && sim.dbLogger != null) {
                sim.dbLogger.close();
            }
        }
        return getStatus();
    }

    /**
     * Reset the simulation session
     */
    public SimulationSession.SessionStatus reset() {
        synchronized (sessionLock) {
            if (currentSession != null) {
                if (currentSession.isRunning()) {
                    currentSession.interrupt();
                }
                StockMarketSim sim = currentSession.getSimulation();
                if (sim != null && sim.dbLogger != null) {
                    try {
                        sim.dbLogger.close();
                    } catch (Exception ignored) {}
                }
                currentSession = null;
            }
            scheduledEvents.clear();
            PolicyEvent.resetIdGenerator();
            MarketShockEvent.resetIdGenerator();
            return getStatus();
        }
    }

    /**
     * Set speed multiplier
     */
    public SimulationSession.SessionStatus setSpeed(double multiplier) {
        if (currentSession != null) {
            currentSession.setSpeedMultiplier(multiplier);
        }
        return getStatus();
    }

    // =====================================================
    // Policy Slot Modification API
    // =====================================================

    /**
     * Get current policy slot values
     */
    public Map<String, Object> getCurrentPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        if (currentSession != null && currentSession.getSimulation() != null
                && currentSession.getSimulation().market != null) {
            PolicySlot slot = currentSession.getSimulation().market.policySlot;
            policy.put("priceLimitRatio", slot.getPriceLimitRatio());
            policy.put("circuitBreakerThreshold", slot.getCircuitBreakerThreshold());
            policy.put("maxLeverageRatio", slot.getMaxLeverageRatio());
            policy.put("settlementDays", slot.getSettlementDays());
            policy.put("benchmarkInterestRate", slot.getBenchmarkInterestRate());
            policy.put("requiredReserveRatio", slot.getRequiredReserveRatio());
            policy.put("stampDutyRate", slot.getStampDutyRate());
            policy.put("capitalGainsTaxRate", slot.getCapitalGainsTaxRate());
            policy.put("dividendTaxRate", slot.getDividendTaxRate());
            policy.put("earningsDisclosurePeriodDays", slot.getEarningsDisclosurePeriodDays());
            policy.put("opinionCensorshipDamping", slot.getOpinionCensorshipDamping());
        } else {
            // Defaults
            policy.put("priceLimitRatio", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_PRICE_LIMIT_RATIO);
            policy.put("circuitBreakerThreshold", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_CIRCUIT_BREAKER_THRESHOLD);
            policy.put("maxLeverageRatio", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_MAX_LEVERAGE_RATIO);
            policy.put("settlementDays", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_SETTLEMENT_DAYS);
            policy.put("benchmarkInterestRate", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_BENCHMARK_INTEREST_RATE);
            policy.put("requiredReserveRatio", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_REQUIRED_RESERVE_RATIO);
            policy.put("stampDutyRate", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_STAMP_DUTY_RATE);
            policy.put("capitalGainsTaxRate", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_CAPITAL_GAINS_TAX_RATE);
            policy.put("dividendTaxRate", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_DIVIDEND_TAX_RATE);
            policy.put("earningsDisclosurePeriodDays", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_EARNINGS_DISCLOSURE_DAYS);
            policy.put("opinionCensorshipDamping", jp.ac.tsukuba.eclab.assetmarketsimulation.Config.POLICY_OPINION_CENSORSHIP_DAMPING);
        }
        return policy;
    }


    // =====================================================
    // Metrics API
    // =====================================================

    public Map<String, Double> getCurrentMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            if (sim.market != null) {
                metrics.put("marketIndex", sim.market.marketIndex);
                metrics.put("totalMarketCap", sim.market.marketTotalMarketCap);
                metrics.put("turnoverRate", sim.market.marketTurnoverRate);
                metrics.put("circuitBreakerTriggered", sim.market.isCircuitBreakerTriggered() ? 1.0 : 0.0);

                double dayReturn = sim.market.indexOpen > 0 ? (sim.market.marketIndex - sim.market.indexOpen) / sim.market.indexOpen : 0.0;
                double cbThreshold = sim.market.policySlot != null ? sim.market.policySlot.getCircuitBreakerThreshold() : 0.0;
                metrics.put("dayReturn", dayReturn);
                metrics.put("circuitBreakerThreshold", cbThreshold);
            }
            metrics.put("activeAgents", (double) currentSession.getActiveAgents());
            metrics.put("socialWealthPool", sim.socialWealthPool);
            metrics.put("day", (double) currentSession.getCurrentDay());
            if (sim.leverageService != null) {
                metrics.put("totalMarginCalls", (double) sim.leverageService.getTotalMarginCalls());
                metrics.put("totalForcedLiquidations", (double) sim.leverageService.getTotalForcedLiquidations());
            }
        }
        return metrics;
    }

    // =====================================================
    // Status Listeners
    // =====================================================

    public void addStatusListener(StatusListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusUpdate() {
        if (currentSession != null) {
            SimulationSession.SessionStatus status = currentSession.toStatus();
            for (StatusListener listener : statusListeners) {
                try {
                    listener.onStatusUpdate(status);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // =====================================================
    // Policy Event Injection (Scheduled Events)
    // =====================================================

    /**
     * Inject a policy change event scheduled for a specific day.
     * Uses MASON's schedule to fire the event at the correct simulation step.
     */
    public PolicyEvent injectPolicyEvent(int targetDay, String policyType, double value, String description) {
        PolicyEvent.PolicyType type =
                PolicyEvent.PolicyType.valueOf(policyType);

        PolicyEvent event =
                new PolicyEvent(targetDay, type, value, description);
        scheduledEvents.add(event);

        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            int stepsPerDay = sim.market != null ? sim.market.STEPS_PER_DAY : 1;
            long targetStep = (long) targetDay * stepsPerDay;
            sim.schedule.scheduleOnce(targetStep, 0, event);
            System.out.println("[INJECT] Policy event scheduled: " + policyType + " -> " + value + " on day " + targetDay);
        } else {
            System.out.println("[INJECT] Policy event pre-scheduled: " + policyType + " -> " + value + " on day " + targetDay);
        }
        return event;
    }

    /**
     * Inject a market shock event scheduled for a specific day.
     */
    public MarketShockEvent injectMarketShock(int targetDay, String shockType, double magnitude,
                                              int halfLifeDays, String targetSector, int targetTraderId, String description) {
        MarketShockEvent.ShockType type =
                MarketShockEvent.ShockType.valueOf(shockType);

        MarketShockEvent event =
                new MarketShockEvent(targetDay, type, magnitude, halfLifeDays, targetSector, targetTraderId, description);
        scheduledEvents.add(event);

        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            int stepsPerDay = sim.market != null ? sim.market.STEPS_PER_DAY : 1;
            long targetStep = (long) targetDay * stepsPerDay;
            sim.schedule.scheduleOnce(targetStep, 0, event);
            System.out.println("[INJECT] Market shock scheduled: " + shockType + " -> " + magnitude + " on day " + targetDay);
        } else {
            System.out.println("[INJECT] Market shock pre-scheduled: " + shockType + " -> " + magnitude + " on day " + targetDay);
        }
        return event;
    }

    /**
     * Get all scheduled policy and shock events and their execution state
     */
    public List<Map<String, Object>> getScheduledEvents() {
        List<Map<String, Object>> list = new ArrayList<>();
        int currentDay = currentSession != null ? currentSession.getCurrentDay() : 0;
        boolean sessionRunningOrDone = currentSession != null && (currentSession.isRunning() || currentSession.isPaused() || "COMPLETED".equals(currentSession.toStatus().state()));
        for (SimulationEvent e : scheduledEvents) {
            Map<String, Object> map = new LinkedHashMap<>(e.toMap());
            boolean isExec = e.isExecuted() || (sessionRunningOrDone && currentDay >= e.getTargetDay());
            map.put("executed", isExec);
            map.put("status", isExec ? "EXECUTED" : "PENDING");
            list.add(map);
        }
        return list;
    }
}

