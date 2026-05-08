package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;

public class ChinaCircuitBreakerScenario implements MarketScenario {
    @Override
    public String getName() {
        return "ChinaCircuitBreakerScenario";
    }

    @Override
    public void apply(StockMarketSim sim) {
        System.out.println("Applying China Circuit Breaker Scenario...");
        
        // Day 100: Introduce 5% circuit breaker
        sim.schedule.scheduleOnce(new PolicyEvent(100, PolicyEvent.PolicyType.CIRCUIT_BREAKER, 0.05, "Enable 5% Circuit Breaker"));
        
        // Day 200: Remove circuit breaker (set high threshold)
        sim.schedule.scheduleOnce(new PolicyEvent(200, PolicyEvent.PolicyType.CIRCUIT_BREAKER, 1.0, "Remove Circuit Breaker"));
        
        // Day 300: Tighten price limits to ±5%
        sim.schedule.scheduleOnce(new PolicyEvent(300, PolicyEvent.PolicyType.PRICE_LIMIT, 0.05, "Tighten Price Limits to 5%"));
        
        // Day 400: Increase leverage to 3x
        sim.schedule.scheduleOnce(new PolicyEvent(400, PolicyEvent.PolicyType.LEVERAGE, 3.0, "Increase Max Leverage to 3x"));
        
        // Day 500: Restore defaults
        sim.schedule.scheduleOnce(new PolicyEvent(500, PolicyEvent.PolicyType.PRICE_LIMIT, 0.10, "Restore 10% Price Limits"));
        sim.schedule.scheduleOnce(new PolicyEvent(500, PolicyEvent.PolicyType.LEVERAGE, 2.0, "Restore 2x Leverage"));
    }
}
