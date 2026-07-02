package com.documind.repository;

import com.documind.model.AuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 返回给前端的审计事件视图。
 */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    List<AuditEventEntity> findAllByOrderByTimestampDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditEventEntity e WHERE e.id IN " +
           "(SELECT ev.id FROM AuditEventEntity ev ORDER BY ev.timestamp ASC LIMIT :excess)")
    void deleteOldest(@Param("excess") int excess);
}
