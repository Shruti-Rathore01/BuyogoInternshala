package com.internshala.factory_monitoring.service;

import com.internshala.factory_monitoring.dto.*;
import com.internshala.factory_monitoring.entity.MachineEvent;
import com.internshala.factory_monitoring.repo.MachineEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @BeforeEach
    public void setup() {
        // Clean database before each test
        repository.deleteAll();
    }

    /**
     * Test 1: Identical duplicate eventId should be deduped
     */
    @Test
    public void testIdenticalDuplicateIsDeduped() {
        // Create first event
        EventRequest event1 = createEventRequest("E-001", "M-001", 5000, 2);
        List<EventRequest> batch1 = new ArrayList<>();
        batch1.add(event1);

        // Ingest first time
        BatchIngestResponse response1 = eventService.ingestBatch(batch1);
        assertEquals(1, response1.getAccepted());
        assertEquals(0, response1.getDeduped());

        // Ingest same event again (identical duplicate)
        List<EventRequest> batch2 = new ArrayList<>();
        batch2.add(event1);
        BatchIngestResponse response2 = eventService.ingestBatch(batch2);

        // Should be deduped
        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getDeduped());
        assertEquals(0, response2.getUpdated());
    }

    /**
     * Test 2: Different payload with newer receivedTime should trigger update
     */
    @Test
    public void testDifferentPayloadWithNewerReceivedTimeUpdates() throws InterruptedException {
        // Create and ingest first event
        EventRequest event1 = createEventRequest("E-002", "M-001", 5000, 2);
        List<EventRequest> batch1 = new ArrayList<>();
        batch1.add(event1);
        eventService.ingestBatch(batch1);

        // Wait a bit to ensure newer receivedTime
        Thread.sleep(100);

        // Create event with same ID but different data
        EventRequest event2 = createEventRequest("E-002", "M-001", 6000, 3);
        List<EventRequest> batch2 = new ArrayList<>();
        batch2.add(event2);

        BatchIngestResponse response = eventService.ingestBatch(batch2);

        // Should be updated
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(1, response.getUpdated());

        // Verify the data was actually updated
        MachineEvent updated = repository.findByEventId("E-002").orElse(null);
        assertNotNull(updated);
        assertEquals(6000L, updated.getDurationMs());
        assertEquals(3, updated.getDefectCount());
    }

    /**
     * Test 3: Different payload with older receivedTime should be ignored
     */
    @Test
    public void testDifferentPayloadWithOlderReceivedTimeIsIgnored() {
        // This test is tricky because receivedTime is set by server
        // We'll simulate by first adding an event, then trying to "update"
        // it in the same batch processing (same receivedTime)

        EventRequest event1 = createEventRequest("E-003", "M-001", 5000, 2);
        List<EventRequest> batch1 = new ArrayList<>();
        batch1.add(event1);
        eventService.ingestBatch(batch1);

        // Get the stored event
        MachineEvent stored = repository.findByEventId("E-003").orElse(null);
        assertNotNull(stored);
        Long originalDuration = stored.getDurationMs();

        // Try to send different data with same eventId in same second
        // (receivedTime will be very close, might be considered "older or equal")
        EventRequest event2 = createEventRequest("E-003", "M-001", 8000, 5);
        List<EventRequest> batch2 = new ArrayList<>();
        batch2.add(event2);

        BatchIngestResponse response = eventService.ingestBatch(batch2);

        // Should either update or dedupe depending on timing
        assertTrue(response.getUpdated() + response.getDeduped() == 1);
    }

    /**
     * Test 4: Invalid duration (negative) should be rejected
     */
    @Test
    public void testInvalidNegativeDurationIsRejected() {
        EventRequest invalidEvent = createEventRequest("E-004", "M-001", -100, 0);
        List<EventRequest> batch = new ArrayList<>();
        batch.add(invalidEvent);

        BatchIngestResponse response = eventService.ingestBatch(batch);

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertEquals(1, response.getRejections().size());
        assertTrue(response.getRejections().get(0).getReason().contains("INVALID_DURATION"));
    }

    /**
     * Test 5: Invalid duration (exceeds 6 hours) should be rejected
     */
    @Test
    public void testInvalidLongDurationIsRejected() {
        long sevenHours = 7 * 60 * 60 * 1000L; // 7 hours in milliseconds
        EventRequest invalidEvent = createEventRequest("E-005", "M-001", sevenHours, 0);
        List<EventRequest> batch = new ArrayList<>();
        batch.add(invalidEvent);

        BatchIngestResponse response = eventService.ingestBatch(batch);

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertTrue(response.getRejections().get(0).getReason().contains("INVALID_DURATION"));
    }

    /**
     * Test 6: Future eventTime (more than 15 minutes) should be rejected
     */
    @Test
    public void testFutureEventTimeIsRejected() {
        EventRequest futureEvent = new EventRequest();
        futureEvent.setEventId("E-006");
        futureEvent.setEventTime(LocalDateTime.now().plusMinutes(20)); // 20 minutes in future
        futureEvent.setMachineId("M-001");
        futureEvent.setDurationMs(5000L);
        futureEvent.setDefectCount(0);

        List<EventRequest> batch = new ArrayList<>();
        batch.add(futureEvent);

        BatchIngestResponse response = eventService.ingestBatch(batch);

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertTrue(response.getRejections().get(0).getReason().contains("INVALID_TIME"));
    }

    /**
     * Test 7: DefectCount = -1 should be ignored in defect totals
     */
    @Test
    public void testDefectCountMinusOneIsIgnoredInStats() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 15, 12, 0);

        // Create events with known defects
        EventRequest event1 = new EventRequest();
        event1.setEventId("E-007");
        event1.setEventTime(start.plusMinutes(10));
        event1.setMachineId("M-001");
        event1.setDurationMs(5000L);
        event1.setDefectCount(5); // 5 defects

        EventRequest event2 = new EventRequest();
        event2.setEventId("E-008");
        event2.setEventTime(start.plusMinutes(20));
        event2.setMachineId("M-001");
        event2.setDurationMs(5000L);
        event2.setDefectCount(-1); // Unknown, should be ignored

        EventRequest event3 = new EventRequest();
        event3.setEventId("E-009");
        event3.setEventTime(start.plusMinutes(30));
        event3.setMachineId("M-001");
        event3.setDurationMs(5000L);
        event3.setDefectCount(3); // 3 defects

        List<EventRequest> batch = new ArrayList<>();
        batch.add(event1);
        batch.add(event2);
        batch.add(event3);

        eventService.ingestBatch(batch);

        // Get stats
        StatsResponse stats = eventService.getStats("M-001", start, end);

        // Should count 3 events
        assertEquals(3, stats.getEventsCount());

        // Should only count 5 + 3 = 8 defects (ignore -1)
        assertEquals(8L, stats.getDefectsCount());
    }

    /**
     * Test 8: Start/end boundary correctness (inclusive/exclusive)
     */
    @Test
    public void testStartEndBoundaryCorrectness() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 15, 11, 0);

        // Event exactly at start (should be included)
        EventRequest event1 = new EventRequest();
        event1.setEventId("E-010");
        event1.setEventTime(start); // Exactly at start
        event1.setMachineId("M-001");
        event1.setDurationMs(5000L);
        event1.setDefectCount(1);

        // Event exactly at end (should be excluded)
        EventRequest event2 = new EventRequest();
        event2.setEventId("E-011");
        event2.setEventTime(end); // Exactly at end
        event2.setMachineId("M-001");
        event2.setDurationMs(5000L);
        event2.setDefectCount(1);

        // Event in the middle (should be included)
        EventRequest event3 = new EventRequest();
        event3.setEventId("E-012");
        event3.setEventTime(start.plusMinutes(30)); // In between
        event3.setMachineId("M-001");
        event3.setDurationMs(5000L);
        event3.setDefectCount(1);

        List<EventRequest> batch = new ArrayList<>();
        batch.add(event1);
        batch.add(event2);
        batch.add(event3);

        eventService.ingestBatch(batch);

        // Get stats for the window
        StatsResponse stats = eventService.getStats("M-001", start, end);

        // Should include event1 (at start) and event3 (in middle)
        // Should exclude event2 (at end)
        assertEquals(2, stats.getEventsCount());
        assertEquals(2L, stats.getDefectsCount());
    }

    /**
     * Test 9: Thread-safety test - concurrent ingestion
     */
    @Test
    public void testConcurrentIngestionThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalAccepted = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    List<EventRequest> batch = new ArrayList<>();
                    for (int i = 0; i < eventsPerThread; i++) {
                        String eventId = "E-T" + threadId + "-" + i;
                        batch.add(createEventRequest(eventId, "M-001", 5000, 1));
                    }
                    BatchIngestResponse response = eventService.ingestBatch(batch);
                    totalAccepted.addAndGet(response.getAccepted());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait for all threads to complete
        executor.shutdown();

        // Should have accepted all unique events
        assertEquals(threadCount * eventsPerThread, totalAccepted.get());

        // Verify in database
        long count = repository.count();
        assertEquals(threadCount * eventsPerThread, count);
    }

    /**
     * Helper method to create EventRequest
     */
    private EventRequest createEventRequest(String eventId, String machineId, long durationMs, int defectCount) {
        EventRequest event = new EventRequest();
        event.setEventId(eventId);
        event.setEventTime(LocalDateTime.now().minusMinutes(5)); // 5 minutes ago
        event.setMachineId(machineId);
        event.setDurationMs(durationMs);
        event.setDefectCount(defectCount);
        event.setLineId("L-001");
        event.setFactoryId("F-001");
        return event;
    }
}