package com.internshala.factory_monitoring.dto;

import java.util.List;

public class TopDefectLineResponse {

    private List<DefectLineStats> lines;

    // Constructors
    public TopDefectLineResponse() {}

    public TopDefectLineResponse(List<DefectLineStats> lines) {
        this.lines = lines;
    }

    // Getters and Setters
    public List<DefectLineStats> getLines() { return lines; }
    public void setLines(List<DefectLineStats> lines) { this.lines = lines; }

    // Inner class
    public static class DefectLineStats {
        private String lineId;
        private Long totalDefects;
        private Long eventCount;
        private Double defectsPercent;

        public DefectLineStats() {}

        public DefectLineStats(String lineId, Long totalDefects, Long eventCount, Double defectsPercent) {
            this.lineId = lineId;
            this.totalDefects = totalDefects;
            this.eventCount = eventCount;
            this.defectsPercent = defectsPercent;
        }

        public String getLineId() { return lineId; }
        public void setLineId(String lineId) { this.lineId = lineId; }

        public Long getTotalDefects() { return totalDefects; }
        public void setTotalDefects(Long totalDefects) { this.totalDefects = totalDefects; }

        public Long getEventCount() { return eventCount; }
        public void setEventCount(Long eventCount) { this.eventCount = eventCount; }

        public Double getDefectsPercent() { return defectsPercent; }
        public void setDefectsPercent(Double defectsPercent) { this.defectsPercent = defectsPercent; }
    }
}