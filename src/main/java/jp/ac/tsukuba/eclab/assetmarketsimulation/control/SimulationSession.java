package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulation session management
 * Handles state, speed control, and lifecycle
 */
public class SimulationSession {

    public enum State {
        IDLE, // Not started
        RUNNING, // Actively running
        PAUSED, // Paused, can resume
        COMPLETED, // Finished normally
        ERROR // Error state
    }

    private final String sessionId;
    private final SimulationConfig config;
    private final long seed;
    private final Instant createdAt;

    private AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private StockMarketSim simulation;
    private Thread simulationThread;

    // Speed control
    private volatile double speedMultiplier = 1.0;
    private volatile int baseStepDelayMs = 50; // Base delay per step

    // Progress tracking
    private volatile int currentDay = 0;
    private volatile long currentStep = 0;
    private volatile int activeAgents = 0;
    private volatile double marketIndex = 0;
    private volatile String lastError = null;

    public SimulationSession(SimulationConfig config) {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.config = config;
        this.seed = System.currentTimeMillis();
        this.createdAt = Instant.now();
    }

    public SimulationSession(SimulationConfig config, long seed) {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.config = config;
        this.seed = seed;
        this.createdAt = Instant.now();
    }

    // ============ Getters ============
    public String getSessionId() {
        return sessionId;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public long getSeed() {
        return seed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state.get();
    }

    public StockMarketSim getSimulation() {
        return simulation;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public long getCurrentStep() {
        return currentStep;
    }

    public int getActiveAgents() {
        return activeAgents;
    }

    public double getMarketIndex() {
        return marketIndex;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public String getLastError() {
        return lastError;
    }

    public int getTotalDays() {
        return config.getSimulationDays();
    }

    public double getProgress() {
        return (double) currentDay / config.getSimulationDays() * 100;
    }

    // ============ Speed Control ============
    public void setSpeedMultiplier(double multiplier) {
        if (multiplier < 0.1)
            multiplier = 0.1;
        if (multiplier > 100)
            multiplier = 100;
        this.speedMultiplier = multiplier;
    }

    public int getEffectiveDelay() {
        return (int) (baseStepDelayMs / speedMultiplier);
    }

    public void setBaseStepDelayMs(int delayMs) {
        this.baseStepDelayMs = Math.max(1, delayMs);
    }

    // ============ State Transitions ============
    public void setSimulation(StockMarketSim sim) {
        this.simulation = sim;
    }

    public void setSimulationThread(Thread thread) {
        this.simulationThread = thread;
    }

    public boolean transitionTo(State newState) {
        State current = state.get();

        // Validate transitions
        switch (newState) {
            case RUNNING:
                if (current != State.IDLE && current != State.PAUSED)
                    return false;
                break;
            case PAUSED:
                if (current != State.RUNNING)
                    return false;
                break;
            case COMPLETED:
            case ERROR:
                // Can transition from any state
                break;
            case IDLE:
                // Reset only from completed or error
                if (current != State.COMPLETED && current != State.ERROR)
                    return false;
                break;
        }

        return state.compareAndSet(current, newState);
    }

    public void updateProgress(int day, long step, int agents, double index) {
        this.currentDay = day;
        this.currentStep = step;
        this.activeAgents = agents;
        this.marketIndex = index;
    }

    public void setError(String error) {
        this.lastError = error;
        state.set(State.ERROR);
    }

    public boolean isRunning() {
        return state.get() == State.RUNNING;
    }

    public boolean isPaused() {
        return state.get() == State.PAUSED;
    }

    public void interrupt() {
        if (simulationThread != null && simulationThread.isAlive()) {
            simulationThread.interrupt();
        }
    }

    // ============ DTO for API ============
    public SessionStatus toStatus() {
        return new SessionStatus(
                sessionId,
                state.get().name(),
                currentDay,
                config.getSimulationDays(),
                getProgress(),
                activeAgents,
                marketIndex,
                speedMultiplier,
                lastError);
    }

    public record SessionStatus(
            String sessionId,
            String state,
            int currentDay,
            int totalDays,
            double progress,
            int activeAgents,
            double marketIndex,
            double speedMultiplier,
            String lastError) {
    }
}
