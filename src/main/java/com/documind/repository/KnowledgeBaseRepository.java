package com.documind.repository;

import com.documind.model.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库仓库，按名称维护知识库元数据。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    Optional<KnowledgeBaseEntity> findByName(String name);

    boolean existsByName(String name);

    List<KnowledgeBaseEntity> findAllByOrderByNameAsc();
}
