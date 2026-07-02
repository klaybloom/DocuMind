package com.documind.repository;

import com.documind.model.KnowledgeGapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识缺口仓库，支持按知识库和时间查询未回答问题。
 */
public interface KnowledgeGapRepository extends JpaRepository<KnowledgeGapEntity, String> {

    List<KnowledgeGapEntity> findByKnowledgeBaseOrderByLastAskedAtDesc(String knowledgeBase);

    Optional<KnowledgeGapEntity> findByKnowledgeBaseAndQuestionIgnoreCase(String knowledgeBase, String question);

    Optional<KnowledgeGapEntity> findByKnowledgeBaseAndId(String knowledgeBase, String id);
}
