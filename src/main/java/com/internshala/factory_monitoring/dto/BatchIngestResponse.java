package com.internshala.factory_monitoring.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchIngestResponse {

    private int accepted;
    private int deduped;
    private int updated;
    private int rejected;
    private List<RejectionDetail> rejections = new ArrayList<>();

    // Constructors
    public BatchIngestResponse() {}

    public BatchIngestResponse(int accepted, int deduped, int updated, int rejected,
                               List<RejectionDetail> rejections) {
        this.accepted = accepted;
        this.deduped = deduped;
        this.updated = updated;
        this.rejected = rejected;
        this.rejections = rejections;
    }

    // Getters and Setters
    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }

    public int getDeduped() { return deduped; }
    public void setDeduped(int deduped) { this.deduped = deduped; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }

    public int getRejected() { return rejected; }
    public void setRejected(int rejected) { this.rejected = rejected; }

    public List<RejectionDetail> getRejections() { return rejections; }
    public void setRejections(List<RejectionDetail> rejections) { this.rejections = rejections; }

    // Inner class for rejection details
    public static class RejectionDetail {
        private String eventId;
        private String reason;

        public RejectionDetail() {}

        public RejectionDetail(String eventId, String reason) {
            this.eventId = eventId;
            this.reason = reason;
        }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}