package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScenarioRegistry {
    
    private static final Map<String, MarketScenario> scenarios = new HashMap<>();
    private static final Map<String, String> descriptions = new HashMap<>();
    
    static {
        register(new DefaultScenario(), "Standard simulation with constant policy parameters.");
        register(new ChinaCircuitBreakerScenario(), "Replicates the 2016 China stock market circuit breaker implementation and subsequent removal.");
    }
    
    public static void register(MarketScenario scenario, String description) {
        scenarios.put(scenario.getName(), scenario);
        descriptions.put(scenario.getName(), description);
    }
    
    public static MarketScenario getScenario(String name) {
        return scenarios.getOrDefault(name, new DefaultScenario());
    }
    
    public static List<ScenarioInfo> listScenarios() {
        List<ScenarioInfo> list = new ArrayList<>();
        for (String name : scenarios.keySet()) {
            list.add(new ScenarioInfo(name, descriptions.get(name)));
        }
        return list;
    }
    
    public static class ScenarioInfo {
        public String name;
        public String description;
        
        public ScenarioInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
