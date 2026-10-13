package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import sim.engine.SimState;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 市场冲击事件 (Market Shock Event)
 * 外生、阶段性、带半衰期衰减的脉冲冲击。
 *
 * 包括：
 * - SENTIMENT_CRASH: 信心骤降 / 市场恐慌 (全市场或散户情绪折价)
 * - RATING_DOWNGRADE: 机构/板块评级下调 (增长预期负向惩罚)
 * - LIQUIDITY_SQUEEZE: 流动性紧缩 (配资冻结与保证金门槛临时上调)
 * - CAPITAL_INJECTION: 定向注资 / 平准救市 (从 socialWealthPool 划转注入市场)
 */
public class MarketShockEvent implements SimulationEvent {

    public enum ShockType {
        SENTIMENT_CRASH,     // 信心骤降 / 市场恐慌
        RATING_DOWNGRADE,    // 行业板块或机构评级下调
        LIQUIDITY_SQUEEZE,   // 流动性紧缩 / 杠杆挤压
        CAPITAL_INJECTION    // 定向注资 / 平准救市基金
    }

    private static final java.util.concurrent.atomic.AtomicInteger ID_GEN = new java.util.concurrent.atomic.AtomicInteger(1000);

    private final int id;
    private final int targetDay;
    private final ShockType shockType;
    private final double magnitude;      // 初始冲击强度 (如 0.20 = 20%)
    private final int halfLifeDays;      // 半衰期天数 (如 5 天, 0 表示一次性或无衰减)
    private final String targetSector;   // 作用行业板块 (如 "ALL", "TECH", "FINANCE")
    private final int targetTraderId;    // 作用目标交易者 (-1 表示全市场/所有机构)
    private final String description;
    private volatile boolean executed = false;

    public MarketShockEvent(int targetDay, ShockType shockType, double magnitude,
                            int halfLifeDays, String targetSector, int targetTraderId, String description) {
        this.id = ID_GEN.getAndIncrement();
        this.targetDay = targetDay;
        this.shockType = shockType;
        this.magnitude = magnitude;
        this.halfLifeDays = Math.max(1, halfLifeDays);
        this.targetSector = (targetSector != null && !targetSector.isBlank()) ? targetSector.toUpperCase() : "ALL";
        this.targetTraderId = targetTraderId;
        this.description = description != null ? description :
                String.format("Market shock: %s (intensity=%.2f, half-life=%dd, target=%s) on day %d",
                        shockType, magnitude, halfLifeDays, this.targetSector, targetDay);
    }

    @Override
    public void step(SimState state) {
        this.executed = true;
        jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim sim =
                (jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim) state;

        if (sim.market == null) return;

        // 注册到 ShockManager 进行时间半衰期衰减跟踪
        if (sim.market.shockManager != null) {
            sim.market.shockManager.registerShock(this);

            // 若为定向注资，执行解耦的资产划转
            if (shockType == ShockType.CAPITAL_INJECTION) {
                sim.market.shockManager.applyCapitalInjection(sim, this);
            }
        }

        System.out.println(String.format("[SHOCK] Day %d triggered: %s (magnitude=%.2f, target=%s)",
                targetDay, shockType, magnitude, targetSector));

        // 记录日志
        if (sim.dbLogger != null) {
            sim.dbLogger.logEvent("MARKET_SHOCK", targetDay, description);
        }
    }

    // Getters
    @Override
    public int getId() { return id; }
    @Override
    public int getTargetDay() { return targetDay; }
    @Override
    public String getEventType() { return "SHOCK"; }
    @Override
    public String getSubtype() { return shockType.name(); }
    public ShockType getShockType() { return shockType; }
    @Override
    public double getValue() { return magnitude; }
    public double getMagnitude() { return magnitude; }
    public int getHalfLifeDays() { return halfLifeDays; }
    public String getTargetSector() { return targetSector; }
    public int getTargetTraderId() { return targetTraderId; }
    @Override
    public String getDescription() { return description; }
    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("day", targetDay);
        map.put("eventType", "SHOCK");
        map.put("subtype", shockType.name());
        map.put("shockType", shockType.name());
        map.put("value", magnitude);
        map.put("magnitude", magnitude);
        map.put("halfLifeDays", halfLifeDays);
        map.put("targetSector", targetSector);
        map.put("targetTraderId", targetTraderId);
        map.put("description", description);
        map.put("executed", executed);
        map.put("status", executed ? "EXECUTED" : "PENDING");
        return map;
    }

    public static void resetIdGenerator() {
        ID_GEN.set(1000);
    }
}
