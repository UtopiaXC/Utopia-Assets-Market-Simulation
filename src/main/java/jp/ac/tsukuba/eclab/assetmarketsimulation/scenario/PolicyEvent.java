package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import sim.engine.SimState;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 政策变更事件 (Scheduled Policy Change Event)
 * 注入 MASON 时间表，在指定模拟日与步数执行并修改 PolicySlot 参数。
 */
public class PolicyEvent implements SimulationEvent {

    public enum PolicyType {
        PRICE_LIMIT,            // L_limit
        CIRCUIT_BREAKER,        // Th_halt
        LEVERAGE,               // Lev_max
        SETTLEMENT,             // N_settle
        BENCHMARK_RATE,         // 基准利率 (年化)
        REQUIRED_RESERVE_RATIO, // 存款准备金率
        STAMP_DUTY,             // 印花税率
        CAPITAL_GAINS_TAX,      // 资本利得税率
        DIVIDEND_TAX,           // 股息红利税率
        DISCLOSURE_PERIOD,      // 财报披露周期 (天数)
        OPINION_CENSORSHIP      // 舆论管制阻尼系数 (0~1)
    }

    private static final java.util.concurrent.atomic.AtomicInteger ID_GEN = new java.util.concurrent.atomic.AtomicInteger(1);

    private final int id;
    private final int targetDay;
    private final PolicyType policyType;
    private final double value;
    private final String description;
    private volatile boolean executed = false;

    public PolicyEvent(int targetDay, PolicyType policyType, double value) {
        this(targetDay, policyType, value, null);
    }

    public PolicyEvent(int targetDay, PolicyType policyType, double value, String description) {
        this.id = ID_GEN.getAndIncrement();
        this.targetDay = targetDay;
        this.policyType = policyType;
        this.value = value;
        this.description = description != null ? description :
                String.format("Policy change: %s -> %.4f on day %d", policyType, value, targetDay);
    }

    @Override
    public void step(SimState state) {
        this.executed = true;
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
            case BENCHMARK_RATE:
                slot.setBenchmarkInterestRate(value);
                System.out.println("[EVENT] Day " + targetDay + ": Benchmark Interest Rate changed to " + (value * 100) + "%");
                break;
            case REQUIRED_RESERVE_RATIO:
                slot.setRequiredReserveRatio(value);
                System.out.println("[EVENT] Day " + targetDay + ": Required Reserve Ratio changed to " + (value * 100) + "%");
                break;
            case STAMP_DUTY:
                slot.setStampDutyRate(value);
                System.out.println("[EVENT] Day " + targetDay + ": Stamp Duty changed to " + (value * 1000) + "‰");
                break;
            case CAPITAL_GAINS_TAX:
                slot.setCapitalGainsTaxRate(value);
                System.out.println("[EVENT] Day " + targetDay + ": Capital Gains Tax changed to " + (value * 100) + "%");
                break;
            case DIVIDEND_TAX:
                slot.setDividendTaxRate(value);
                System.out.println("[EVENT] Day " + targetDay + ": Dividend Tax changed to " + (value * 100) + "%");
                break;
            case DISCLOSURE_PERIOD:
                slot.setEarningsDisclosurePeriodDays((int) value);
                System.out.println("[EVENT] Day " + targetDay + ": Earnings Disclosure Period changed to " + (int) value + " days");
                break;
            case OPINION_CENSORSHIP:
                slot.setOpinionCensorshipDamping(value);
                System.out.println("[EVENT] Day " + targetDay + ": Opinion Censorship Damping changed to " + String.format("%.2f", value));
                break;
        }

        // Log the event
        if (sim.dbLogger != null) {
            sim.dbLogger.logEvent("POLICY_CHANGE", targetDay, description);
        }
    }

    // Getters for serialization & SimulationEvent interface
    @Override
    public int getId() { return id; }
    @Override
    public int getTargetDay() { return targetDay; }
    @Override
    public String getEventType() { return "POLICY"; }
    @Override
    public String getSubtype() { return policyType.name(); }
    public PolicyType getPolicyType() { return policyType; }
    @Override
    public double getValue() { return value; }
    @Override
    public String getDescription() { return description; }
    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("day", targetDay);
        map.put("eventType", "POLICY");
        map.put("subtype", policyType.name());
        map.put("policyType", policyType.name());
        map.put("value", value);
        map.put("magnitude", value);
        map.put("description", description);
        map.put("executed", executed);
        map.put("status", executed ? "EXECUTED" : "PENDING");
        return map;
    }

    public static void resetIdGenerator() {
        ID_GEN.set(1);
    }
}
