package com.internshala.factory_monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchIngestRequest {

    @NotEmpty(message = "Events list cannot be empty")
    @Valid
    private List<EventRequest> events;

    // Constructors
    public BatchIngestRequest() {}

    public BatchIngestRequest(List<EventRequest> events) {
        this.events = events;
    }

    // Getters and Setters
    public List<EventRequest> getEvents() { return events; }
    public void setEvents(List<EventRequest> events) { this.events = events; }
}