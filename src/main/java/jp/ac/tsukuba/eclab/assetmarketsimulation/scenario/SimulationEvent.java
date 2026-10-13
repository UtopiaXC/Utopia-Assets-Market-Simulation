package jp.ac.tsukuba.eclab.assetmarketsimulation.scenario;

import sim.engine.Steppable;
import java.util.Map;

/**
 * 通用模拟事件接口 (Universal Simulation Event Interface)
 * 统一调度政策干预 (PolicyEvent) 与市场冲击 (MarketShockEvent)
 */
public interface SimulationEvent extends Steppable {

    int getId();

    int getTargetDay();

    String getEventType(); // "POLICY" or "SHOCK"

    String getSubtype();

    double getValue();

    String getDescription();

    boolean isExecuted();

    Map<String, Object> toMap();
}
