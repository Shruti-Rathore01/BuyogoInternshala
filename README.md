# Factory Monitoring System - Backend Assignment

A Spring Boot application for monitoring factory machines, processing events, and providing real-time statistics.

## Table of Contents
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Data Model](#data-model)
- [Dedupe/Update Logic](#dedupeupdate-logic)
- [Thread Safety](#thread-safety)
- [Performance Strategy](#performance-strategy)
- [Edge Cases & Assumptions](#edge-cases--assumptions)
- [Setup & Run Instructions](#setup--run-instructions)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Future Improvements](#future-improvements)

---

## Architecture

The application follows a **layered architecture** pattern:
```
┌─────────────────────────────────────┐
│         Controller Layer            │  ← REST API endpoints
│     (EventController.java)          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Service Layer              │  ← Business logic
│      (EventService.java)            │  ← Validation, deduplication
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│       Repository Layer              │  ← Database operations
│  (MachineEventRepository.java)      │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│          Database (H2)              │  ← In-memory storage
└─────────────────────────────────────┘
```

### Key Components:

1. **Controller**: Handles HTTP requests/responses
2. **Service**: Contains business logic (validation, deduplication, calculations)
3. **Repository**: Database access layer (Spring Data JPA)
4. **Entity**: Database table mapping (MachineEvent)
5. **DTOs**: Data transfer objects for API communication

---

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Database**: H2 (in-memory)
- **ORM**: Spring Data JPA / Hibernate
- **Testing**: JUnit 5
- **Build Tool**: Maven

---

## Data Model

### MachineEvent Entity (Database Table)
```sql
CREATE TABLE machine_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_time TIMESTAMP NOT NULL,
    received_time TIMESTAMP NOT NULL,
    machine_id VARCHAR(255) NOT NULL,
    duration_ms BIGINT NOT NULL,
    defect_count INT NOT NULL,
    line_id VARCHAR(255),
    factory_id VARCHAR(255)
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_event_id ON machine_events(event_id);
CREATE INDEX idx_machine_time ON machine_events(machine_id, event_time);
```

### Field Descriptions:

- **id**: Auto-generated primary key
- **event_id**: Unique identifier for each event (used for deduplication)
- **event_time**: When the event actually occurred (used for queries)
- **received_time**: When the system received the event (used for update logic)
- **machine_id**: Identifier of the machine that sent the event
- **duration_ms**: Duration of the event in milliseconds
- **defect_count**: Number of defects (-1 means unknown)
- **line_id**: Production line identifier (optional)
- **factory_id**: Factory identifier (optional)

---

## Dedupe/Update Logic

### Decision Flow:
```
New Event Arrives
       ↓
Does eventId exist in DB?
       ↓
   ┌───NO────┐              ┌───YES───┐
   │         │              │         │
   ↓         │              ↓         │
Accept    ←──┘      Compare payload  │
(Insert)              ↓               │
                 Identical? ──────────┘
                      ↓
              ┌──YES──┼──NO──┐
              ↓               ↓
          Dedupe         Compare
          (Ignore)    receivedTime
                           ↓
                  ┌───Newer──┼──Older/Same──┐
                  ↓                          ↓
              Update                     Dedupe
           (Replace)                    (Ignore)
```

### Implementation Details:

**1. Payload Comparison:**
We compare ALL fields (except receivedTime and id):
- eventTime
- machineId
- durationMs
- defectCount
- lineId
- factoryId

**2. Update Decision:**
- If payload is **identical** → DEDUPE (ignore)
- If payload is **different** → Check receivedTime
    - If new receivedTime is **AFTER** existing → UPDATE
    - If new receivedTime is **BEFORE or SAME** → DEDUPE (ignore old update)

**3. Code Implementation:**
```java
if (isIdenticalPayload(existing, new)) {
    // Same data = duplicate
    dedupe();
} else {
    // Different data
    if (newReceivedTime.isAfter(existingReceivedTime)) {
        update();  // Newer wins
    } else {
        dedupe();  // Older/same, ignore
    }
}
```

---

## Thread Safety

The application handles **concurrent requests** safely through multiple mechanisms:

### 1. Synchronized Method
```java
public synchronized BatchIngestResponse ingestBatch(...)
```
- Only one thread can execute `ingestBatch()` at a time
- Prevents race conditions during duplicate detection
- Simple and effective for this use case

### 2. Database Constraints
```java
@Column(nullable = false, unique = true)
private String eventId;
```
- **UNIQUE constraint** on `eventId` prevents duplicate keys
- Database-level protection against concurrent inserts

### 3. Transactional Processing
```java
@Transactional
public synchronized BatchIngestResponse ingestBatch(...)
```
- Each batch is processed in a single transaction
- ACID properties ensure data consistency
- If any error occurs, entire batch is rolled back

### Why This Approach?

✅ **Simple**: Easy to understand and maintain  
✅ **Reliable**: No race conditions possible  
✅ **Sufficient**: For the assignment's concurrency requirements

### Trade-off:
❌ **Throughput**: Synchronized method limits parallel processing

**Alternative for production:** Use optimistic locking with `@Version` or distributed locks (Redis).

---

## Performance Strategy

**Goal**: Process 1000 events in under 1 second

### Optimizations Implemented:

#### 1. **Batch Database Operations**
```java
repository.saveAll(events);  // Single batch insert
```
- Instead of 1000 individual saves, we do 1 batch operation
- Reduces database round trips

#### 2. **Database Indexing**
```java
@Index(name = "idx_event_id", columnList = "eventId", unique = true)
@Index(name = "idx_machine_time", columnList = "machineId,eventTime")
```
- Fast lookup by `eventId` for duplicate detection
- Fast filtering by `machineId + eventTime` for stats queries

#### 3. **Efficient Queries**
```sql
-- Direct aggregation in database
SELECT SUM(defect_count) FROM machine_events WHERE ...
```
- Calculations done in database (faster than Java loops)
- No need to fetch all records into memory

#### 4. **In-Memory Database**
- H2 database runs in memory (no disk I/O)
- Extremely fast read/write operations

#### 5. **Minimal Object Creation**
- Reuse objects where possible
- Avoid unnecessary copying of data

### Benchmark Results:
See [BENCHMARK.md](BENCHMARK.md) for detailed performance measurements.

---

## Edge Cases & Assumptions

### Edge Cases Handled:

1. **Negative Duration**
    - Validation: Reject if `durationMs < 0`
    - Reason: Negative time doesn't make sense

2. **Excessive Duration**
    - Validation: Reject if `durationMs > 6 hours`
    - Assumption: Machines don't run continuously for more than 6 hours

3. **Future Events**
    - Validation: Reject if `eventTime` is more than 15 minutes in future
    - Reason: Likely a clock synchronization issue or error

4. **Unknown Defects**
    - Handling: `defectCount = -1` means "unknown"
    - Behavior: Store event but exclude from defect calculations
    - Example: Event is stored, but not counted in `defectsCount`

5. **Concurrent Updates**
    - Same `eventId` from multiple threads
    - Solution: Synchronized method + database UNIQUE constraint

6. **Empty Time Windows**
    - Query with no matching events
    - Result: Return counts as 0, rate as 0.0

7. **Identical Timestamps**
    - Multiple events at exact same `eventTime`
    - Handled correctly by inclusive/exclusive boundaries

### Assumptions Made:

1. **receivedTime is server-controlled**
    - Client cannot set receivedTime
    - Always set to `LocalDateTime.now()` by server

2. **eventTime is trusted**
    - We assume machines have synchronized clocks
    - Only reject if wildly incorrect (> 15 min future)

3. **String IDs are sufficient**
    - `eventId`, `machineId`, `lineId`, `factoryId` are strings
    - No validation of ID formats

4. **Single application instance**
    - Synchronized method works for single JVM
    - For distributed deployment, need distributed locking

5. **Data retention not specified**
    - Currently stores all events forever
    - In production, would need archival/cleanup strategy

---

## Setup & Run Instructions

### Prerequisites:
- Java 17 or higher
- Maven 3.6+

### Steps:

1. **Clone/Extract the project**
```bash
   cd factory-monitoring
```

2. **Build the project**
```bash
   mvn clean install
```

3. **Run the application**
```bash
   mvn spring-boot:run
```

OR
```bash
   java -jar target/factory-monitoring-0.0.1-SNAPSHOT.jar
```

4. **Verify it's running**
    - Open browser: http://localhost:8080/api/health
    - Should see: `"Factory Monitoring System is running!"`

5. **Access H2 Console (optional)**
    - URL: http://localhost:8080/h2-console
    - JDBC URL: `jdbc:h2:mem:factorydb`
    - Username: `sa`
    - Password: (leave empty)

---

## API Endpoints

### 1. Batch Ingest Events

**Endpoint:** `POST /api/events/batch`

**Request Body:**
```json
[
  {
    "eventId": "E-001",
    "eventTime": "2026-01-15T10:00:00",
    "machineId": "M-001",
    "durationMs": 5000,
    "defectCount": 2,
    "lineId": "L-001",
    "factoryId": "F-001"
  }
]
```

**Response:**
```json
{
  "accepted": 1,
  "deduped": 0,
  "updated": 0,
  "rejected": 0,
  "rejections": []
}
```

---

### 2. Get Machine Statistics

**Endpoint:** `GET /api/stats`

**Query Parameters:**
- `machineId`: Machine identifier (required)
- `start`: Start time (ISO 8601, inclusive)
- `end`: End time (ISO 8601, exclusive)

**Example:**
```
GET /api/stats?machineId=M-001&start=2026-01-15T00:00:00&end=2026-01-15T06:00:00
```

**Response:**
```json
{
  "machineId": "M-001",
  "start": "2026-01-15T00:00:00",
  "end": "2026-01-15T06:00:00",
  "eventsCount": 120,
  "defectsCount": 10,
  "avgDefectRate": 1.67,
  "status": "Healthy"
}
```

**Status Logic:**
- `"Healthy"`: avgDefectRate < 2.0
- `"Warning"`: avgDefectRate >= 2.0

---

### 3. Top Defect Lines

**Endpoint:** `GET /api/stats/top-defect-lines`

**Query Parameters:**
- `factoryId`: Factory identifier (required)
- `from`: Start time (ISO 8601, inclusive)
- `to`: End time (ISO 8601, exclusive)
- `limit`: Max results (default: 10)

**Example:**
```
GET /api/stats/top-defect-lines?factoryId=F-001&from=2026-01-15T00:00:00&to=2026-01-15T06:00:00&limit=5
```

**Response:**
```json
{
  "lines": [
    {
      "lineId": "L-002",
      "totalDefects": 50,
      "eventCount": 100,
      "defectsPercent": 50.0
    },
    {
      "lineId": "L-001",
      "totalDefects": 30,
      "eventCount": 120,
      "defectsPercent": 25.0
    }
  ]
}
```

---

## Testing

### Run All Tests:
```bash
mvn test
```

### Test Coverage:

We have **9 comprehensive tests** covering:

1. ✅ Duplicate detection (identical payload)
2. ✅ Update detection (different payload, newer)
3. ✅ Ignore old updates (different payload, older)
4. ✅ Reject negative duration
5. ✅ Reject excessive duration (> 6 hours)
6. ✅ Reject future events (> 15 minutes)
7. ✅ DefectCount = -1 ignored in calculations
8. ✅ Start/end boundary correctness
9. ✅ Thread-safety (concurrent ingestion)

### Expected Output:
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

---

## Future Improvements

Given more time, I would implement:

### 1. **Better Concurrency**
- Remove synchronized method
- Use optimistic locking with `@Version`
- Allow parallel processing of different machines

### 2. **Caching**
- Cache frequently queried statistics
- Use Redis or Caffeine cache
- Invalidate on new events

### 3. **Data Archival**
- Move old events to archive tables
- Implement data retention policies
- Keep only last 30 days in active table

### 4. **Distributed Support**
- Use distributed locks (Redis/Zookeeper)
- Support multiple application instances
- Load balancing

### 5. **Advanced Validation**
- Validate ID formats (regex patterns)
- Check if machineId exists in master data
- Validate eventTime against machine schedule

### 6. **Monitoring & Alerting**
- Expose metrics (Prometheus/Micrometer)
- Real-time alerts for high defect rates
- Dashboard for visualization

### 7. **API Improvements**
- Pagination for large result sets
- Filtering by multiple criteria
- Export to CSV/Excel

### 8. **Database Optimization**
- Switch to PostgreSQL for production
- Partitioning by date
- Read replicas for queries

### 9. **Security**
- API authentication (JWT/OAuth2)
- Rate limiting
- Input sanitization

### 10. **Better Error Handling**
- Custom exception classes
- Global exception handler
- Detailed error messages

---

## License

This is an assignment project for Internshala Backend Intern position.

---

## Contact

For any questions or clarifications, please contact the developer.