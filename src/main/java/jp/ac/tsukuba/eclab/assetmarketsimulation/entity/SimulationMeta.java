package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class SimulationMeta {
    private Integer id;
    private Long seed;
    private String startTime;
    private String scenarioName;
    private Integer numStocks;
    private Integer numAgents;
    private Integer simulationDays;
    private Integer stepsPerDay;
    private String configJson;
}
