package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

/**
 * Extensible event log - parameters_json allows storing arbitrary event data.
 */
public class EventLogEntity {
    private String eventId;
    private Integer day;
    private String eventType;
    private String source;
    private String parametersJson;
    private String description;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
