package com.internshala.factory_monitoring.repo;


import com.internshala.factory_monitoring.entity.MachineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MachineEventRepository extends JpaRepository<MachineEvent, Long> {

    // Find event by eventId (for duplicate detection)
    Optional<MachineEvent> findByEventId(String eventId);

    // Find all events for a machine within a time window
    @Query("SELECT e FROM MachineEvent e WHERE e.machineId = :machineId " +
            "AND e.eventTime >= :start AND e.eventTime < :end")
    List<MachineEvent> findByMachineIdAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // Count defects for a machine in a time window (excluding unknown defects)
    @Query("SELECT COALESCE(SUM(e.defectCount), 0) FROM MachineEvent e " +
            "WHERE e.machineId = :machineId " +
            "AND e.eventTime >= :start AND e.eventTime < :end " +
            "AND e.defectCount >= 0")
    Long sumDefectsByMachineAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<MachineEvent> findAllByEventIdIn(List<String> eventIds);

    // Count total events for a machine in a time window
    @Query("SELECT COUNT(e) FROM MachineEvent e " +
            "WHERE e.machineId = :machineId " +
            "AND e.eventTime >= :start AND e.eventTime < :end")
    Long countByMachineIdAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // For top defect lines endpoint
    @Query("SELECT e.lineId, " +
            "SUM(CASE WHEN e.defectCount >= 0 THEN e.defectCount ELSE 0 END) as totalDefects, " +
            "COUNT(e) as eventCount " +
            "FROM MachineEvent e " +
            "WHERE e.factoryId = :factoryId " +
            "AND e.eventTime >= :start AND e.eventTime < :end " +
            "AND e.lineId IS NOT NULL " +
            "GROUP BY e.lineId " +
            "ORDER BY totalDefects DESC")
    List<Object[]> findTopDefectLines(
            @Param("factoryId") String factoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}