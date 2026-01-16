package com.internshala.factory_monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class EventRequest {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotNull(message = "eventTime is required")
    private LocalDateTime eventTime;

    @NotBlank(message = "machineId is required")
    private String machineId;

    @NotNull(message = "durationMs is required")
    private Long durationMs;

    @NotNull(message = "defectCount is required")
    private Integer defectCount;

    private String lineId;
    private String factoryId;

    // Constructors
    public EventRequest() {}

    public EventRequest(String eventId, LocalDateTime eventTime, String machineId,
                        Long durationMs, Integer defectCount, String lineId, String factoryId) {
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.machineId = machineId;
        this.durationMs = durationMs;
        this.defectCount = defectCount;
        this.lineId = lineId;
        this.factoryId = factoryId;
    }

    // Getters and Setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getDefectCount() { return defectCount; }
    public void setDefectCount(Integer defectCount) { this.defectCount = defectCount; }

    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }

    public String getFactoryId() { return factoryId; }
    public void setFactoryId(String factoryId) { this.factoryId = factoryId; }
}