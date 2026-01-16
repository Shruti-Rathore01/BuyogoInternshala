package com.internshala.factory_monitoring.service;

import com.internshala.factory_monitoring.dto.BatchIngestResponse;
import com.internshala.factory_monitoring.dto.EventRequest;
import com.internshala.factory_monitoring.dto.StatsResponse;
import com.internshala.factory_monitoring.dto.TopDefectLineResponse;
import com.internshala.factory_monitoring.entity.MachineEvent;
import com.internshala.factory_monitoring.repo.MachineEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final MachineEventRepository repository;

    @Autowired
    public EventService(MachineEventRepository repository) {
        this.repository = repository;
    }

    // Constants for validation
    private static final long MAX_DURATION_MS = 6 * 60 * 60 * 1000L;
    private static final long FUTURE_TIME_THRESHOLD_MINUTES = 15;
    private static final double HEALTHY_THRESHOLD = 2.0;

    @Transactional
    public synchronized BatchIngestResponse ingestBatch(List<EventRequest> events) {
        log.info("Processing batch of {} events", events.size());

        BatchIngestResponse response = new BatchIngestResponse();
        response.setAccepted(0);
        response.setDeduped(0);
        response.setUpdated(0);
        response.setRejected(0);
        response.setRejections(new ArrayList<>());

        LocalDateTime now = LocalDateTime.now();

        // OPTIMIZATION 1: Collect all eventIds first
        List<String> eventIds = events.stream()
                .map(EventRequest::getEventId)
                .collect(Collectors.toList());

        // OPTIMIZATION 2: Batch fetch all existing events (single query instead of N queries)
        Map<String, MachineEvent> existingEventsMap = repository.findAllByEventIdIn(eventIds)
                .stream()
                .collect(Collectors.toMap(MachineEvent::getEventId, e -> e));

        // OPTIMIZATION 3: Prepare lists for batch operations
        List<MachineEvent> toInsert = new ArrayList<>();
        List<MachineEvent> toUpdate = new ArrayList<>();

        // Process each event
        for (EventRequest eventRequest : events) {
            String validationError = validateEvent(eventRequest, now);
            if (validationError != null) {
                response.setRejected(response.getRejected() + 1);
                BatchIngestResponse.RejectionDetail rejection = new BatchIngestResponse.RejectionDetail();
                rejection.setEventId(eventRequest.getEventId());
                rejection.setReason(validationError);
                response.getRejections().add(rejection);
                continue;
            }

            MachineEvent existingEvent = existingEventsMap.get(eventRequest.getEventId());

            if (existingEvent != null) {
                if (isIdenticalPayload(existingEvent, eventRequest)) {
                    response.setDeduped(response.getDeduped() + 1);
                    log.debug("Deduped event: {}", eventRequest.getEventId());
                } else {
                    if (now.isAfter(existingEvent.getReceivedTime())) {
                        updateEvent(existingEvent, eventRequest, now);
                        toUpdate.add(existingEvent);
                        response.setUpdated(response.getUpdated() + 1);
                        log.debug("Updated event: {}", eventRequest.getEventId());
                    } else {
                        response.setDeduped(response.getDeduped() + 1);
                        log.debug("Ignored older update for event: {}", eventRequest.getEventId());
                    }
                }
            } else {
                MachineEvent newEvent = convertToEntity(eventRequest, now);
                toInsert.add(newEvent);
                response.setAccepted(response.getAccepted() + 1);
                log.debug("Accepted new event: {}", eventRequest.getEventId());
            }
        }

        // OPTIMIZATION 4: Batch save all at once (instead of one-by-one)
        if (!toInsert.isEmpty()) {
            repository.saveAll(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            repository.saveAll(toUpdate);
        }

        log.info("Batch processing complete: accepted={}, deduped={}, updated={}, rejected={}",
                response.getAccepted(), response.getDeduped(),
                response.getUpdated(), response.getRejected());

        return response;
    }



    private String validateEvent(EventRequest event, LocalDateTime now) {
        if (event.getDurationMs() < 0) {
            return "INVALID_DURATION: durationMs cannot be negative";
        }
        if (event.getDurationMs() > MAX_DURATION_MS) {
            return "INVALID_DURATION: durationMs cannot exceed 6 hours";
        }

        long minutesInFuture = ChronoUnit.MINUTES.between(now, event.getEventTime());
        if (minutesInFuture > FUTURE_TIME_THRESHOLD_MINUTES) {
            return "INVALID_TIME: eventTime is more than 15 minutes in the future";
        }

        return null;
    }

    private boolean isIdenticalPayload(MachineEvent existing, EventRequest request) {
        return existing.getEventTime().equals(request.getEventTime()) &&
                existing.getMachineId().equals(request.getMachineId()) &&
                existing.getDurationMs().equals(request.getDurationMs()) &&
                existing.getDefectCount().equals(request.getDefectCount()) &&
                objectEquals(existing.getLineId(), request.getLineId()) &&
                objectEquals(existing.getFactoryId(), request.getFactoryId());
    }

    private boolean objectEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void updateEvent(MachineEvent existing, EventRequest request, LocalDateTime receivedTime) {
        existing.setEventTime(request.getEventTime());
        existing.setReceivedTime(receivedTime);
        existing.setMachineId(request.getMachineId());
        existing.setDurationMs(request.getDurationMs());
        existing.setDefectCount(request.getDefectCount());
        existing.setLineId(request.getLineId());
        existing.setFactoryId(request.getFactoryId());
    }

    private MachineEvent convertToEntity(EventRequest request, LocalDateTime receivedTime) {
        MachineEvent event = new MachineEvent();
        event.setEventId(request.getEventId());
        event.setEventTime(request.getEventTime());
        event.setReceivedTime(receivedTime);
        event.setMachineId(request.getMachineId());
        event.setDurationMs(request.getDurationMs());
        event.setDefectCount(request.getDefectCount());
        event.setLineId(request.getLineId());
        event.setFactoryId(request.getFactoryId());
        return event;
    }

    public StatsResponse getStats(String machineId, LocalDateTime start, LocalDateTime end) {
        log.info("Getting stats for machine={}, start={}, end={}", machineId, start, end);

        Long eventsCount = repository.countByMachineIdAndTimeRange(machineId, start, end);
        Long defectsCount = repository.sumDefectsByMachineAndTimeRange(machineId, start, end);

        double windowHours = ChronoUnit.SECONDS.between(start, end) / 3600.0;
        double avgDefectRate = windowHours > 0 ? defectsCount / windowHours : 0.0;
        avgDefectRate = Math.round(avgDefectRate * 100.0) / 100.0;

        String status = avgDefectRate < HEALTHY_THRESHOLD ? "Healthy" : "Warning";

        // Create response without builder
        StatsResponse response = new StatsResponse();
        response.setMachineId(machineId);
        response.setStart(start);
        response.setEnd(end);
        response.setEventsCount(eventsCount);
        response.setDefectsCount(defectsCount);
        response.setAvgDefectRate(avgDefectRate);
        response.setStatus(status);

        return response;
    }

    public TopDefectLineResponse getTopDefectLines(String factoryId, LocalDateTime start,
                                                   LocalDateTime end, int limit) {
        log.info("Getting top {} defect lines for factory={}, start={}, end={}",
                limit, factoryId, start, end);

        List<Object[]> results = repository.findTopDefectLines(factoryId, start, end);

        List<TopDefectLineResponse.DefectLineStats> lines = results.stream()
                .limit(limit)
                .map(row -> {
                    String lineId = (String) row[0];
                    Long totalDefects = ((Number) row[1]).longValue();
                    Long eventCount = ((Number) row[2]).longValue();

                    double defectsPercent = eventCount > 0
                            ? (totalDefects * 100.0 / eventCount)
                            : 0.0;
                    defectsPercent = Math.round(defectsPercent * 100.0) / 100.0;

                    // Create stats without builder
                    TopDefectLineResponse.DefectLineStats stats = new TopDefectLineResponse.DefectLineStats();
                    stats.setLineId(lineId);
                    stats.setTotalDefects(totalDefects);
                    stats.setEventCount(eventCount);
                    stats.setDefectsPercent(defectsPercent);
                    return stats;
                })
                .collect(Collectors.toList());

        // Create response without builder
        TopDefectLineResponse response = new TopDefectLineResponse();
        response.setLines(lines);
        return response;
    }
}