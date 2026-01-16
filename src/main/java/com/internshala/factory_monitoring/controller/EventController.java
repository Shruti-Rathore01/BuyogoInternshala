package com.internshala.factory_monitoring.controller;

import com.internshala.factory_monitoring.dto.BatchIngestResponse;
import com.internshala.factory_monitoring.dto.EventRequest;
import com.internshala.factory_monitoring.dto.StatsResponse;
import com.internshala.factory_monitoring.dto.TopDefectLineResponse;
import com.internshala.factory_monitoring.service.EventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;

    @Autowired
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Endpoint 1: Batch ingest events
     * POST /api/events/batch
     */
    @PostMapping("/events/batch")
    public ResponseEntity<BatchIngestResponse> ingestBatch(@Valid @RequestBody List<EventRequest> events) {
        BatchIngestResponse response = eventService.ingestBatch(events);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint 2: Get statistics for a machine
     * GET /api/stats?machineId=M-001&start=2026-01-15T00:00:00&end=2026-01-15T06:00:00
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam String machineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        StatsResponse response = eventService.getStats(machineId, start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint 3: Get top defect lines
     * GET /api/stats/top-defect-lines?factoryId=F01&from=2026-01-15T00:00:00&to=2026-01-15T06:00:00&limit=10
     */
    @GetMapping("/stats/top-defect-lines")
    public ResponseEntity<TopDefectLineResponse> getTopDefectLines(
            @RequestParam String factoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") int limit) {

        TopDefectLineResponse response = eventService.getTopDefectLines(factoryId, from, to, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Factory Monitoring System is running!");
    }
}