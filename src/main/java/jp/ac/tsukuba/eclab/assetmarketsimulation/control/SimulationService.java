package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.event.InterventionEvent;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.event.InterventionEvents;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.TestScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.MarketScenario;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import org.springframework.stereotype.Service;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.*;
import java.util.concurrent.*;

/**
 * Core simulation control service
 * Manages simulation lifecycle, speed, events, and state
 */
@Service
@SuppressWarnings("unused") // FAVAR fields are placeholders for future integration
public class SimulationService implements InterventionAPI {

    private SimulationSession currentSession;
    private final Object sessionLock = new Object();

    // Event management
    private final List<InterventionEvent> pendingEvents = new CopyOnWriteArrayList<>();
    private final List<InterventionEvent> eventHistory = new CopyOnWriteArrayList<>();

    // EA callback
    private FitnessCallback fitnessCallback;

    // FAVAR state (placeholder for future FAVAR model integration)
    private volatile double[] currentFactorValues;
    private volatile double[][] currentLoadingMatrix;

    // Status listeners (for WebSocket)
    private final List<StatusListener> statusListeners = new CopyOnWriteArrayList<>();

    public interface StatusListener {
        void onStatusUpdate(SimulationSession.SessionStatus status);

        void onEventExecuted(InterventionEvent event);
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
                    null, "NO_SESSION", 0, 0, 0, 0, 0, 1.0, null, 0, 0);
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

            // Clear previous events
            pendingEvents.clear();
            eventHistory.clear();

            // Create new session
            currentSession = new SimulationSession(config);

            // Create and configure simulation
            StockMarketSim sim = new StockMarketSim(currentSession.getSeed());
            sim.numStocks = config.getNumStocks();
            sim.simulationDays = config.getSimulationDays();
            sim.setSimulationName(config.getSimulationName());
            sim.setStepsPerDay(config.getStepsPerDay());

            // Set scenario
            MarketScenario scenario = createScenario(config.getScenarioName());
            sim.setScenario(scenario);

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

    private MarketScenario createScenario(String name) {
        // TODO: Add more scenarios
        if ("EmptyScenario".equalsIgnoreCase(name)) {
            return new MarketScenario() {
                @Override
                public String getName() {
                    return "Empty Scenario";
                }

                @Override
                public void apply(StockMarketSim sim) {
                }
            };
        }
        return new TestScenario();
    }

    private void runSimulation() {
        StockMarketSim sim = currentSession.getSimulation();
        SimulationConfig config = currentSession.getConfig();

        try {
            sim.start();

            // Register event checker
            sim.schedule.scheduleRepeating(0, 999, new Steppable() {
                @Override
                public void step(SimState state) {
                    checkAndExecuteEvents(sim);
                }
            });

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

            // Notify EA callback
            if (fitnessCallback != null) {
                fitnessCallback.onSimulationComplete(
                        currentSession.getSessionId(),
                        getCurrentMetrics());
            }

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

    private void checkAndExecuteEvents(StockMarketSim sim) {
        int currentDay = sim.market.getCurrentDay();
        long currentStep = sim.schedule.getSteps();

        Iterator<InterventionEvent> it = pendingEvents.iterator();
        while (it.hasNext()) {
            InterventionEvent event = it.next();

            boolean shouldExecute = false;
            if (event.getTargetStep() > 0 && currentStep >= event.getTargetStep()) {
                shouldExecute = true;
            } else if (event.getTargetDay() > 0 && currentDay >= event.getTargetDay()) {
                shouldExecute = true;
            }

            if (shouldExecute && !event.isExecuted()) {
                event.apply(sim);
                eventHistory.add(event);
                pendingEvents.remove(event);
                notifyEventExecuted(event);
            }
        }
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
     * Set speed multiplier
     */
    public SimulationSession.SessionStatus setSpeed(double multiplier) {
        if (currentSession != null) {
            currentSession.setSpeedMultiplier(multiplier);
        }
        return getStatus();
    }

    // =====================================================
    // InterventionAPI Implementation
    // =====================================================

    @Override
    public String injectEvent(InterventionEvent event) {
        pendingEvents.add(event);
        return event.getEventId();
    }

    @Override
    public List<String> injectEvents(List<InterventionEvent> events) {
        List<String> ids = new ArrayList<>();
        for (InterventionEvent event : events) {
            ids.add(injectEvent(event));
        }
        return ids;
    }

    @Override
    public boolean cancelEvent(String eventId) {
        return pendingEvents.removeIf(e -> e.getEventId().equals(eventId));
    }

    @Override
    public List<InterventionEvent> getPendingEvents() {
        return new ArrayList<>(pendingEvents);
    }

    @Override
    public List<InterventionEvent> getEventHistory() {
        return new ArrayList<>(eventHistory);
    }

    @Override
    public String applyFactorIntervention(double[][] factorMatrix, double[][] loadingMatrix) {
        InterventionEvents.MatrixInterventionEvent event = new InterventionEvents.MatrixInterventionEvent(factorMatrix,
                loadingMatrix);
        event.setTargetDay(currentSession != null ? currentSession.getCurrentDay() + 1 : 1);
        return injectEvent(event);
    }

    @Override
    public void setFactorInfluence(double[] factorValues, double[][] loadingMatrix) {
        this.currentFactorValues = factorValues;
        this.currentLoadingMatrix = loadingMatrix;
        // TODO: Apply in simulation step
    }

    @Override
    public void clearFactorInfluence() {
        this.currentFactorValues = null;
        this.currentLoadingMatrix = null;
    }

    @Override
    public void registerFitnessCallback(FitnessCallback callback) {
        this.fitnessCallback = callback;
    }

    @Override
    public Map<String, Double> getCurrentMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            if (sim.market != null) {
                metrics.put("marketIndex", sim.market.marketIndex);
                metrics.put("totalMarketCap", sim.market.marketTotalMarketCap);
                metrics.put("turnoverRate", sim.market.marketTurnoverRate);
            }
            metrics.put("activeAgents", (double) currentSession.getActiveAgents());
            metrics.put("socialWealthPool", sim.socialWealthPool);
            metrics.put("day", (double) currentSession.getCurrentDay());
        }
        return metrics;
    }

    @Override
    public void modifyAgentBehavior(int agentId, String agentType, Map<String, Object> behaviorModifier) {
        // TODO: Implement LLM behavior modification
        System.out.println("[LLM] Agent behavior modification requested but not yet implemented");
    }

    @Override
    public void injectAgentDecision(int agentId, String decision, String stockId, double intensity) {
        // TODO: Implement LLM decision injection
        System.out.println("[LLM] Agent decision injection requested but not yet implemented");
    }

    @Override
    public Map<String, Object> getAgentState(int agentId) {
        Map<String, Object> state = new HashMap<>();
        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            for (int i = 0; i < sim.traders.size(); i++) {
                Object obj = sim.traders.get(i);
                if (obj instanceof BaseTrader) {
                    BaseTrader t = (BaseTrader) obj;
                    if (t.traderId == agentId) {
                        state.put("traderId", t.traderId);
                        state.put("traderType", t.traderType);
                        state.put("riskTolerance", t.riskTolerance);
                        state.put("cash", t.portfolio.cash);
                        state.put("stockValue", t.portfolio.getTotalStockValue());
                        state.put("isActive", t.isActive());
                        break;
                    }
                }
            }
        }
        return state;
    }

    @Override
    public Map<String, Object> getMarketContext() {
        Map<String, Object> context = new HashMap<>();
        if (currentSession != null && currentSession.getSimulation() != null) {
            StockMarketSim sim = currentSession.getSimulation();
            if (sim.market != null) {
                context.put("day", sim.market.getCurrentDay());
                context.put("marketIndex", sim.market.marketIndex);
                context.put("totalMarketCap", sim.market.marketTotalMarketCap);
                context.put("volume", sim.market.totalVolumeThisDay);
                context.put("turnover", sim.market.totalTurnoverThisDay);
            }
            context.put("activeAgents", currentSession.getActiveAgents());
            context.put("socialWealthPool", sim.socialWealthPool);
        }
        return context;
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

    private void notifyEventExecuted(InterventionEvent event) {
        for (StatusListener listener : statusListeners) {
            try {
                listener.onEventExecuted(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // =====================================================
    // Convenience methods for creating events
    // =====================================================

    public String injectRateCut(int targetDay, double liquidityPerAgent, double riskBoost) {
        InterventionEvents.RateCutEvent event = new InterventionEvents.RateCutEvent(liquidityPerAgent, riskBoost);
        event.setTargetDay(targetDay);
        return injectEvent(event);
    }

    public String injectRateHike(int targetDay, double liquidityRatio, double riskDrop) {
        InterventionEvents.RateHikeEvent event = new InterventionEvents.RateHikeEvent(liquidityRatio, riskDrop);
        event.setTargetDay(targetDay);
        return injectEvent(event);
    }

    public String injectSectorSentiment(int targetDay, String sector, double multiplier) {
        Sector s = Sector.valueOf(sector.toUpperCase());
        InterventionEvents.SectorSentimentEvent event = new InterventionEvents.SectorSentimentEvent(s, multiplier);
        event.setTargetDay(targetDay);
        return injectEvent(event);
    }

    public String injectSectorFundamental(int targetDay, String sector, double epsChange) {
        Sector s = Sector.valueOf(sector.toUpperCase());
        InterventionEvents.SectorFundamentalEvent event = new InterventionEvents.SectorFundamentalEvent(s, epsChange);
        event.setTargetDay(targetDay);
        return injectEvent(event);
    }
}
