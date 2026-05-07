package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

/**
 * Extensible event log - parameters_json allows storing arbitrary event data
 * (e.g. future sentiment contagion events).
 */
@Data
public class EventLogEntity {
    private String eventId;
    private Integer day;
    private String eventType;
    private String source;
    private String parametersJson;
    private String description;
}
