package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

/**
 * Macro statistics data
 */
public class MacroStatsDTO {

    // Population data
    public List<PopulationData> populationHistory;

    // Wealth structure data
    public List<WealthData> wealthHistory;

    // Assets by agent type
    public List<AgentTypeData> agentTypeAssets;

    // Risk tolerance by agent type
    public List<AgentTypeData> agentTypeRisk;

    /**
     * Daily active agent population
     */
    public static class PopulationData {
        public int day;
        public int count;

        public PopulationData() {
        }

        public PopulationData(int day, int count) {
            this.day = day;
            this.count = count;
        }
    }

    /**
     * Wealth structure (social pool, savings, liquidity)
     */
    public static class WealthData {
        public int day;
        public double socialWealthPool;
        public double savings;
        public double liquidity;

        public WealthData() {
        }

        public WealthData(int day, double socialWealthPool, double savings, double liquidity) {
            this.day = day;
            this.socialWealthPool = socialWealthPool;
            this.savings = savings;
            this.liquidity = liquidity;
        }
    }

    /**
     * Data by agent type
     */
    public static class AgentTypeData {
        public int day;
        public String traderType;
        public double value;

        public AgentTypeData() {
        }

        public AgentTypeData(int day, String traderType, double value) {
            this.day = day;
            this.traderType = traderType;
            this.value = value;
        }
    }
}
