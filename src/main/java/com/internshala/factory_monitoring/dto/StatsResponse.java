
package com.internshala.factory_monitoring.dto;

import java.time.LocalDateTime;

public class StatsResponse {

    private String machineId;
    private LocalDateTime start;
    private LocalDateTime end;
    private Long eventsCount;
    private Long defectsCount;
    private Double avgDefectRate;
    private String status;

    // Constructors
    public StatsResponse() {}

    public StatsResponse(String machineId, LocalDateTime start, LocalDateTime end,
                         Long eventsCount, Long defectsCount, Double avgDefectRate, String status) {
        this.machineId = machineId;
        this.start = start;
        this.end = end;
        this.eventsCount = eventsCount;
        this.defectsCount = defectsCount;
        this.avgDefectRate = avgDefectRate;
        this.status = status;
    }

    // Getters and Setters
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public LocalDateTime getStart() { return start; }
    public void setStart(LocalDateTime start) { this.start = start; }

    public LocalDateTime getEnd() { return end; }
    public void setEnd(LocalDateTime end) { this.end = end; }

    public Long getEventsCount() { return eventsCount; }
    public void setEventsCount(Long eventsCount) { this.eventsCount = eventsCount; }

    public Long getDefectsCount() { return defectsCount; }
    public void setDefectsCount(Long defectsCount) { this.defectsCount = defectsCount; }

    public Double getAvgDefectRate() { return avgDefectRate; }
    public void setAvgDefectRate(Double avgDefectRate) { this.avgDefectRate = avgDefectRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}