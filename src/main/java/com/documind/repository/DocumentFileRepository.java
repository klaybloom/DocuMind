package com.documind.repository;

import com.documind.model.DocumentFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentFileRepository extends JpaRepository<DocumentFileEntity, Long> {

    List<DocumentFileEntity> findByKnowledgeBase(String knowledgeBase);

    Optional<DocumentFileEntity> findByKnowledgeBaseAndFileName(String knowledgeBase, String fileName);

    long deleteByKnowledgeBaseAndFileName(String knowledgeBase, String fileName);

    @Query("SELECT DISTINCT e.knowledgeBase FROM DocumentFileEntity e")
    List<String> findDistinctKnowledgeBase();
}
