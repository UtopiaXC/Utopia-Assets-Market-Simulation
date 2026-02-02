package jp.ac.tsukuba.eclab.assetmarketsimulation.control.event;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;

/**
 * Predefined intervention event types
 */
public class InterventionEvents {

    /**
     * 降息事件：注入流动性
     */
    public static class RateCutEvent extends InterventionEvent {
        private double liquidityInjection;
        private double riskToleranceBoost;

        public RateCutEvent(double liquidityInjection, double riskToleranceBoost) {
            super("RATE_CUT");
            this.liquidityInjection = liquidityInjection;
            this.riskToleranceBoost = riskToleranceBoost;
            setParameter("liquidityInjection", liquidityInjection);
            setParameter("riskToleranceBoost", riskToleranceBoost);
        }

        @Override
        public void apply(StockMarketSim sim) {
            System.out.println("[EVENT] Applying Rate Cut: +" + liquidityInjection + " per agent");
            sim.intervention.injectLiquidity(liquidityInjection);
            if (riskToleranceBoost != 0) {
                sim.intervention.adjustRiskTolerance(riskToleranceBoost);
            }
            markExecuted();
        }

        @Override
        public String getDescription() {
            return String.format("Rate Cut: Inject %.0f per agent, risk +%.2f",
                    liquidityInjection, riskToleranceBoost);
        }
    }

    /**
     * 加息事件：收紧流动性
     */
    public static class RateHikeEvent extends InterventionEvent {
        private double liquidityRatio; // % of cash to remove
        private double riskToleranceDrop;

        public RateHikeEvent(double liquidityRatio, double riskToleranceDrop) {
            super("RATE_HIKE");
            this.liquidityRatio = liquidityRatio;
            this.riskToleranceDrop = riskToleranceDrop;
            setParameter("liquidityRatio", liquidityRatio);
            setParameter("riskToleranceDrop", riskToleranceDrop);
        }

        @Override
        public void apply(StockMarketSim sim) {
            System.out.println("[EVENT] Applying Rate Hike: -" + (liquidityRatio * 100) + "% liquidity");
            sim.intervention.tightenLiquidity(liquidityRatio, riskToleranceDrop);
            markExecuted();
        }

        @Override
        public String getDescription() {
            return String.format("Rate Hike: Remove %.0f%% liquidity, risk -%.2f",
                    liquidityRatio * 100, riskToleranceDrop);
        }
    }

    /**
     * 板块情绪提振
     */
    public static class SectorSentimentEvent extends InterventionEvent {
        private Sector sector;
        private double sentimentMultiplier;

        public SectorSentimentEvent(Sector sector, double sentimentMultiplier) {
            super("SECTOR_SENTIMENT");
            this.sector = sector;
            this.sentimentMultiplier = sentimentMultiplier;
            setParameter("sector", sector.name());
            setParameter("sentimentMultiplier", sentimentMultiplier);
        }

        @Override
        public void apply(StockMarketSim sim) {
            System.out.println("[EVENT] Sector Sentiment: " + sector + " x" + sentimentMultiplier);
            sim.intervention.triggerSectorSentimentShock(sector, sentimentMultiplier);
            markExecuted();
        }

        @Override
        public String getDescription() {
            return String.format("Sector %s sentiment x%.2f", sector, sentimentMultiplier);
        }
    }

    /**
     * 板块基本面冲击
     */
    public static class SectorFundamentalEvent extends InterventionEvent {
        private Sector sector;
        private double epsChange; // e.g., -0.3 means -30% EPS

        public SectorFundamentalEvent(Sector sector, double epsChange) {
            super("SECTOR_FUNDAMENTAL");
            this.sector = sector;
            this.epsChange = epsChange;
            setParameter("sector", sector.name());
            setParameter("epsChange", epsChange);
        }

        @Override
        public void apply(StockMarketSim sim) {
            System.out.println("[EVENT] Sector Fundamental: " + sector + " EPS " +
                    (epsChange > 0 ? "+" : "") + (epsChange * 100) + "%");
            sim.intervention.triggerSectorFundamentalShock(sector, epsChange);
            markExecuted();
        }

        @Override
        public String getDescription() {
            return String.format("Sector %s EPS %+.0f%%", sector, epsChange * 100);
        }
    }

    /**
     * 重置情绪（泡沫破裂）
     */
    public static class ResetSentimentEvent extends InterventionEvent {
        public ResetSentimentEvent() {
            super("RESET_SENTIMENT");
        }

        @Override
        public void apply(StockMarketSim sim) {
            System.out.println("[EVENT] Resetting all sentiment multipliers");
            sim.intervention.resetSentiment();
            markExecuted();
        }

        @Override
        public String getDescription() {
            return "Reset all sentiment to baseline";
        }
    }

    /**
     * 自定义矩阵干预 (FAVAR预留)
     */
    public static class MatrixInterventionEvent extends InterventionEvent {
        private double[][] factorMatrix;
        private double[][] loadingMatrix;

        public MatrixInterventionEvent(double[][] factorMatrix, double[][] loadingMatrix) {
            super("MATRIX_INTERVENTION");
            this.factorMatrix = factorMatrix;
            this.loadingMatrix = loadingMatrix;
            setParameter("factorRows", factorMatrix != null ? factorMatrix.length : 0);
            setParameter("loadingRows", loadingMatrix != null ? loadingMatrix.length : 0);
        }

        @Override
        public void apply(StockMarketSim sim) {
            // TODO: Implement FAVAR model integration
            System.out.println("[EVENT] Matrix Intervention (FAVAR) - NOT IMPLEMENTED YET");
            markExecuted();
        }

        @Override
        public String getDescription() {
            int fRows = factorMatrix != null ? factorMatrix.length : 0;
            int lRows = loadingMatrix != null ? loadingMatrix.length : 0;
            return String.format("Matrix Intervention: %dx factors, %dx loadings", fRows, lRows);
        }

        public double[][] getFactorMatrix() {
            return factorMatrix;
        }

        public double[][] getLoadingMatrix() {
            return loadingMatrix;
        }
    }

    /**
     * 代理行为修改 (LLM预留)
     */
    public static class AgentBehaviorEvent extends InterventionEvent {
        private int targetAgentId; // -1 for all agents
        private String behaviorType;
        private double intensity;

        public AgentBehaviorEvent(int targetAgentId, String behaviorType, double intensity) {
            super("AGENT_BEHAVIOR");
            this.targetAgentId = targetAgentId;
            this.behaviorType = behaviorType;
            this.intensity = intensity;
            setParameter("targetAgentId", targetAgentId);
            setParameter("behaviorType", behaviorType);
            setParameter("intensity", intensity);
        }

        @Override
        public void apply(StockMarketSim sim) {
            // TODO: Implement LLM-based behavior modification
            System.out.println("[EVENT] Agent Behavior Modification (LLM) - NOT IMPLEMENTED YET");
            markExecuted();
        }

        @Override
        public String getDescription() {
            String target = targetAgentId == -1 ? "ALL" : String.valueOf(targetAgentId);
            return String.format("Agent %s: %s (intensity=%.2f)", target, behaviorType, intensity);
        }
    }
}
