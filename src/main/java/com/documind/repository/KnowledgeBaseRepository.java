package com.documind.repository;

import com.documind.model.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    Optional<KnowledgeBaseEntity> findByName(String name);

    boolean existsByName(String name);

    List<KnowledgeBaseEntity> findAllByOrderByNameAsc();
}
