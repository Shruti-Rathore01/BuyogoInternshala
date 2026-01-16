package com.internshala.factory_monitoring.service;


import com.internshala.factory_monitoring.dto.BatchIngestResponse;
import com.internshala.factory_monitoring.dto.EventRequest;
import com.internshala.factory_monitoring.repo.MachineEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class BenchmarkTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @BeforeEach
    public void setup() {
        repository.deleteAll();
    }

    /**
     * Benchmark: Ingest 1000 events
     * Requirement: Must complete in under 1 second
     */
    @Test
    public void benchmarkIngest1000Events() {
        System.out.println("\n========================================");
        System.out.println("BENCHMARK: Ingesting 1000 Events");
        System.out.println("========================================\n");

        // Generate 1000 unique events
        List<EventRequest> events = generateEvents(1000);

        // Warm-up run (to initialize JVM, load classes, etc.)
        eventService.ingestBatch(events.subList(0, 100));
        repository.deleteAll();

        // Actual benchmark - run 5 times
        long totalTime = 0;
        int runs = 5;

        for (int run = 1; run <= runs; run++) {
            repository.deleteAll(); // Clean before each run

            long startTime = System.currentTimeMillis();
            BatchIngestResponse response = eventService.ingestBatch(events);
            long endTime = System.currentTimeMillis();

            long timeTaken = endTime - startTime;
            totalTime += timeTaken;

            System.out.printf("Run %d: %d ms (%d events/sec) - Accepted: %d%n",
                    run,
                    timeTaken,
                    (1000 * 1000) / timeTaken,
                    response.getAccepted()
            );

            assertEquals(1000, response.getAccepted());
            assertEquals(0, response.getRejected());
        }

        long avgTime = totalTime / runs;
        System.out.printf("%nAverage: %d ms (%d events/sec)%n",
                avgTime,
                (1000 * 1000) / avgTime
        );

        // Check if requirement is met
        if (avgTime < 1000) {
            System.out.println("✅ PASS: Processed 1000 events in under 1 second!");
        } else {
            System.out.println("❌ FAIL: Took longer than 1 second");
        }

        System.out.println("\n========================================\n");

        // Assert performance requirement
        assertTrue(avgTime < 1000,
                String.format("Performance requirement not met! Took %d ms (must be < 1000ms)", avgTime));
    }

    /**
     * Benchmark: Duplicate detection performance
     */
    @Test
    public void benchmarkDuplicateDetection() {
        System.out.println("\n========================================");
        System.out.println("BENCHMARK: Duplicate Detection");
        System.out.println("========================================\n");

        List<EventRequest> events = generateEvents(1000);

        // First ingestion
        eventService.ingestBatch(events);

        // Benchmark duplicate detection
        long startTime = System.currentTimeMillis();
        BatchIngestResponse response = eventService.ingestBatch(events); // Same events again
        long endTime = System.currentTimeMillis();

        long timeTaken = endTime - startTime;

        System.out.printf("Deduplication of 1000 events: %d ms%n", timeTaken);
        System.out.printf("Deduped: %d, Accepted: %d%n",
                response.getDeduped(),
                response.getAccepted()
        );

        assertEquals(1000, response.getDeduped());
        assertEquals(0, response.getAccepted());

        System.out.println("\n========================================\n");
    }

    /**
     * Benchmark: Concurrent ingestion
     */
    @Test
    public void benchmarkConcurrentIngestion() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("BENCHMARK: Concurrent Ingestion");
        System.out.println("========================================\n");

        int threadCount = 10;
        int eventsPerThread = 100;

        List<Thread> threads = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                List<EventRequest> events = new ArrayList<>();
                for (int i = 0; i < eventsPerThread; i++) {
                    String eventId = String.format("E-T%d-%04d", threadId, i);
                    events.add(createEvent(eventId, "M-001"));
                }
                eventService.ingestBatch(events);
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        long endTime = System.currentTimeMillis();
        long timeTaken = endTime - startTime;

        System.out.printf("Concurrent ingestion (%d threads × %d events): %d ms%n",
                threadCount, eventsPerThread, timeTaken);
        System.out.printf("Total events: %d%n", repository.count());

        assertEquals(threadCount * eventsPerThread, repository.count());

        System.out.println("\n========================================\n");
    }

    /**
     * Helper: Generate test events
     */
    private List<EventRequest> generateEvents(int count) {
        List<EventRequest> events = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 15, 10, 0);

        for (int i = 0; i < count; i++) {
            EventRequest event = new EventRequest();
            event.setEventId(String.format("E-%04d", i));
            event.setEventTime(baseTime.plusSeconds(i * 10));
            event.setMachineId(String.format("M-%03d", (i % 10) + 1));
            event.setDurationMs(5000L + (i % 1000));
            event.setDefectCount(i % 5);
            event.setLineId(String.format("L-%03d", (i % 5) + 1));
            event.setFactoryId("F-001");
            events.add(event);
        }

        return events;
    }

    /**
     * Helper: Create single event
     */
    private EventRequest createEvent(String eventId, String machineId) {
        EventRequest event = new EventRequest();
        event.setEventId(eventId);
        event.setEventTime(LocalDateTime.now().minusMinutes(5));
        event.setMachineId(machineId);
        event.setDurationMs(5000L);
        event.setDefectCount(1);
        event.setLineId("L-001");
        event.setFactoryId("F-001");
        return event;
    }
}