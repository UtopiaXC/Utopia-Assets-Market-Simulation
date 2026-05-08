package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;

public class DefaultScenario implements MarketScenario {
    @Override
    public String getName() {
        return "DefaultScenario";
    }

    @Override
    public void apply(StockMarketSim sim) {
        // No extra scheduled events. Runs with config defaults.
        System.out.println("Applying Default Scenario...");
    }
}
