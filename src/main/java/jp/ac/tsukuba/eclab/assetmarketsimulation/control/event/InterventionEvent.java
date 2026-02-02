package jp.ac.tsukuba.eclab.assetmarketsimulation.control.event;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all intervention events
 * Extensible for FAVAR, EA, LLM integrations
 */
public abstract class InterventionEvent {

    public enum Source {
        MANUAL, // User injected via UI
        SCENARIO, // From predefined scenario
        FAVAR, // FAVAR model (future)
        EA, // Evolutionary algorithm (future)
        LLM // Language model (future)
    }

    protected final String eventId;
    protected final String eventType;
    protected int targetDay; // When to trigger (in days)
    protected int targetStep; // When to trigger (in steps, more precise)
    protected Source source;
    protected Map<String, Object> parameters;
    protected boolean executed = false;

    protected InterventionEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString().substring(0, 8);
        this.eventType = eventType;
        this.source = Source.MANUAL;
        this.parameters = new HashMap<>();
    }

    // ============ Abstract methods ============

    /**
     * Apply this event to the simulation
     * 
     * @param sim The simulation instance
     */
    public abstract void apply(StockMarketSim sim);

    /**
     * Get human-readable description
     */
    public abstract String getDescription();

    // ============ Getters/Setters ============

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getTargetDay() {
        return targetDay;
    }

    public void setTargetDay(int targetDay) {
        this.targetDay = targetDay;
    }

    public int getTargetStep() {
        return targetStep;
    }

    public void setTargetStep(int targetStep) {
        this.targetStep = targetStep;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    public boolean isExecuted() {
        return executed;
    }

    public void markExecuted() {
        this.executed = true;
    }

    // ============ JSON serialization helpers ============

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("eventType", eventType);
        map.put("targetDay", targetDay);
        map.put("targetStep", targetStep);
        map.put("source", source.name());
        map.put("parameters", parameters);
        map.put("executed", executed);
        map.put("description", getDescription());
        return map;
    }
}
