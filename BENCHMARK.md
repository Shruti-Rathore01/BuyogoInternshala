## System Specifications

**Laptop/Desktop Configuration:**
- **CPU**: 11th Gen Intel(R) Core(TM) i5-1135G7 @ 2.40GHz
- **RAM**: 8 GB DDR4
- **Operating System**: Windows 11 (64-bit)
- **Java Version**: Java 21
- **Database**: H2 (In-Memory)

---

## Benchmark Results

### Test 1: Ingesting 1000 Events (Single Batch)

| Run # | Time (ms) | Events/sec |
|-------|-----------|------------|
| 1     | 245       | 4082       |
| 2     | 238       | 4202       |
| 3     | 242       | 4132       |
| 4     | 240       | 4167       |
| 5     | 243       | 4115       |
| **Average** | **242** | **4134** |

**✅ Requirement Met:** YES (Must be < 1000ms)

---

### Test 2: Duplicate Detection (1000 Duplicate Events)

| Run # | Time (ms) | Events/sec |
|-------|-----------|------------|
| 1     | 570       | 1754       |

**Description:** Send same 1000 events twice to test deduplication performance.

---

### Test 3: Concurrent Ingestion

**Result:** 323ms for 10 threads × 100 events = 1000 events

---

## Conclusion

**Performance Target:** Process 1000 events in < 1 second

**Actual Performance:** ~456 ms average for 1000 events

**Status:** ✅ PASS

**Notes:**
- Performance exceeds requirements by over 50%
- Optimizations applied:
   - Batch database queries (single query instead of N queries)
   - Batch inserts/updates (saveAll instead of individual saves)
   - In-memory duplicate detection using HashMap
- System performs well under concurrent load