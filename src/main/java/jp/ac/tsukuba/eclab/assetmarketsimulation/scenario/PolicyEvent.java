package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import sim.engine.SimState;
import sim.engine.Steppable;

/**
 * A scheduled policy change event.
 * Injected into the MASON schedule to execute at a specific simulation step.
 *
 * Usage:
 *   PolicyEvent event = new PolicyEvent(targetDay, "PRICE_LIMIT", 0.05);
 *   sim.schedule.scheduleOnce(targetStep, event);
 *
 * Or via the REST API / Scenario system:
 *   controlApi.injectPolicyEvent({ day: 100, policyType: "CIRCUIT_BREAKER", value: 0.05 })
 */
public class PolicyEvent implements Steppable {

    public enum PolicyType {
        PRICE_LIMIT,       // L_limit
        CIRCUIT_BREAKER,   // Th_halt
        LEVERAGE,          // Lev_max
        SETTLEMENT         // N_settle
    }

    private final int targetDay;
    private final PolicyType policyType;
    private final double value;
    private final String description;

    public PolicyEvent(int targetDay, PolicyType policyType, double value) {
        this(targetDay, policyType, value, null);
    }

    public PolicyEvent(int targetDay, PolicyType policyType, double value, String description) {
        this.targetDay = targetDay;
        this.policyType = policyType;
        this.value = value;
        this.description = description != null ? description :
                String.format("Policy change: %s -> %.4f on day %d", policyType, value, targetDay);
    }

    @Override
    public void step(SimState state) {
        jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim sim =
                (jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim) state;

        if (sim.market == null || sim.market.policySlot == null) return;

        PolicySlot slot = sim.market.policySlot;
        switch (policyType) {
            case PRICE_LIMIT:
                slot.setPriceLimitRatio(value);
                System.out.println("[EVENT] Day " + targetDay + ": Price Limit changed to ±" + (value * 100) + "%");
                break;
            case CIRCUIT_BREAKER:
                slot.setCircuitBreakerThreshold(value);
                System.out.println("[EVENT] Day " + targetDay + ": Circuit Breaker changed to ±" + (value * 100) + "%");
                break;
            case LEVERAGE:
                slot.setMaxLeverageRatio(value);
                System.out.println("[EVENT] Day " + targetDay + ": Max Leverage changed to " + value + "x");
                break;
            case SETTLEMENT:
                slot.setSettlementDays((int) value);
                System.out.println("[EVENT] Day " + targetDay + ": Settlement changed to T+" + (int) value);
                break;
        }

        // Log the event
        if (sim.dbLogger != null) {
            sim.dbLogger.logEvent("POLICY_CHANGE", targetDay, description);
        }
    }

    // Getters for serialization
    public int getTargetDay() { return targetDay; }
    public PolicyType getPolicyType() { return policyType; }
    public double getValue() { return value; }
    public String getDescription() { return description; }
}
