package com.documind.repository;

import com.documind.model.KnowledgeBaseOwnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeBaseOwnerRepository extends JpaRepository<KnowledgeBaseOwnerEntity, Long> {

    List<KnowledgeBaseOwnerEntity> findByKnowledgeBaseOrderByUsernameAsc(String knowledgeBase);

    List<KnowledgeBaseOwnerEntity> findByUsernameOrderByKnowledgeBaseAsc(String username);

    boolean existsByKnowledgeBaseAndUsername(String knowledgeBase, String username);

    long countByKnowledgeBase(String knowledgeBase);

    void deleteByKnowledgeBaseAndUsername(String knowledgeBase, String username);

    long deleteByKnowledgeBaseAndUsernameNotIn(String knowledgeBase, Collection<String> usernames);
}
