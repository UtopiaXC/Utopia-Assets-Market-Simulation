package jp.ac.tsukuba.eclab.assetmarketsimulation.control;

import jp.ac.tsukuba.eclab.assetmarketsimulation.control.event.InterventionEvent;
import java.util.List;
import java.util.Map;

/**
 * External Intervention API Interface
 * Designed for extensibility with FAVAR, Evolutionary Algorithms, and LLM
 * 
 * This interface can be called by:
 * - Manual UI input
 * - Evolutionary algorithms (batch event generation)
 * - FAVAR model (matrix-based intervention)
 * - LLM agents (behavior modification)
 */
public interface InterventionAPI {

    /**
     * Inject a single intervention event
     * 
     * @param event The event to inject
     * @return Event ID for tracking
     */
    String injectEvent(InterventionEvent event);

    /**
     * Inject multiple events (for EA batch generation)
     * 
     * @param events List of events to inject
     * @return List of event IDs
     */
    List<String> injectEvents(List<InterventionEvent> events);

    /**
     * Cancel a scheduled event
     * 
     * @param eventId Event ID to cancel
     * @return true if cancelled successfully
     */
    boolean cancelEvent(String eventId);

    /**
     * Get all pending (not yet executed) events
     * 
     * @return List of pending events
     */
    List<InterventionEvent> getPendingEvents();

    /**
     * Get event history (executed events)
     * 
     * @return List of executed events
     */
    List<InterventionEvent> getEventHistory();

    // =====================================================
    // FAVAR Interface (Factor-Augmented VAR)
    // =====================================================

    /**
     * Apply factor-based intervention (FAVAR model)
     * 
     * @param factorMatrix  Factor time series [T x K]
     *                      T = time steps, K = number of factors
     * @param loadingMatrix Loading matrix [K x N]
     *                      K = number of factors, N = number of observable series
     * @return Intervention ID
     */
    String applyFactorIntervention(double[][] factorMatrix, double[][] loadingMatrix);

    /**
     * Set persistent factor influence
     * Called each step to modify market based on current factor values
     * 
     * @param factorValues  Current factor values [K]
     * @param loadingMatrix Loading matrix [K x N]
     */
    void setFactorInfluence(double[] factorValues, double[][] loadingMatrix);

    /**
     * Clear factor influence
     */
    void clearFactorInfluence();

    // =====================================================
    // Evolutionary Algorithm Interface
    // =====================================================

    /**
     * Register a callback for EA to receive fitness feedback
     * 
     * @param callback Called after each simulation run with fitness metrics
     */
    void registerFitnessCallback(FitnessCallback callback);

    /**
     * Get current market metrics for fitness evaluation
     * 
     * @return Map of metric names to values
     */
    Map<String, Double> getCurrentMetrics();

    /**
     * Fitness callback interface for EA integration
     */
    @FunctionalInterface
    interface FitnessCallback {
        void onSimulationComplete(String sessionId, Map<String, Double> metrics);
    }

    // =====================================================
    // LLM Agent Interface
    // =====================================================

    /**
     * Modify agent behavior (for LLM-driven agents)
     * 
     * @param agentId          Agent ID, -1 for all agents of a type
     * @param agentType        Agent type filter (null for all types)
     * @param behaviorModifier Behavior modification parameters
     *                         Possible keys:
     *                         - "riskTolerance": double, absolute change
     *                         - "tradingFrequency": double, multiplier
     *                         - "herdingBias": double, 0-1 (0=independent, 1=full
     *                         herding)
     *                         - "newsReactivity": double, multiplier for news
     *                         impact
     *                         - "custom": Map for extensible parameters
     */
    void modifyAgentBehavior(int agentId, String agentType, Map<String, Object> behaviorModifier);

    /**
     * Inject agent decision override
     * LLM can directly specify buy/sell decisions for specific agents
     * 
     * @param agentId   Target agent
     * @param decision  "BUY", "SELL", "HOLD"
     * @param stockId   Target stock (null for portfolio-wide)
     * @param intensity 0-1, how strongly to follow the decision
     */
    void injectAgentDecision(int agentId, String decision, String stockId, double intensity);

    /**
     * Get agent state for LLM context
     * 
     * @param agentId Agent ID
     * @return Agent state as JSON-serializable map
     */
    Map<String, Object> getAgentState(int agentId);

    /**
     * Get market context for LLM
     * 
     * @return Current market state as JSON-serializable map
     */
    Map<String, Object> getMarketContext();
}
