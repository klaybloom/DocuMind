package com.documind.service;

import com.documind.model.DocumentFileEntity;
import com.documind.repository.AuditEventRepository;
import com.documind.repository.DocumentFileRepository;
import com.documind.repository.KnowledgeGapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonMigrationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesManifestFromKnowledgeBaseSubdirectory() throws Exception {
        Path hr = tempDir.resolve("HR");
        Files.createDirectories(hr);
        Files.writeString(hr.resolve(".documind-files.json"), """
                [
                  {
                    "knowledgeBase": "HR",
                    "fileName": "policy.txt",
                    "sizeBytes": 123,
                    "contentType": "text/plain",
                    "owner": "Alice",
                    "uploadedBy": "admin",
                    "uploadedAt": "2026-06-24T00:00:00Z",
                    "lastIndexedAt": "2026-06-24T01:00:00Z",
                    "indexStatus": "INDEXED",
                    "chunkCount": 2,
                    "error": null
                  }
                ]
                """);

        DocumentFileRepository fileRepository = mock(DocumentFileRepository.class);
        KnowledgeGapRepository gapRepository = mock(KnowledgeGapRepository.class);
        AuditEventRepository auditRepository = mock(AuditEventRepository.class);
        List<DocumentFileEntity> saved = new ArrayList<>();
        when(fileRepository.count()).thenReturn(0L);
        when(fileRepository.save(any())).thenAnswer(inv -> {
            DocumentFileEntity entity = inv.getArgument(0);
            saved.add(entity);
            return entity;
        });

        JsonMigrationService migrationService = new JsonMigrationService(
                fileRepository,
                gapRepository,
                auditRepository
        );
        ReflectionTestUtils.setField(migrationService, "documentsPath", tempDir.toString());

        migrationService.migrate();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getKnowledgeBase()).isEqualTo("HR");
        assertThat(saved.get(0).getFileName()).isEqualTo("policy.txt");
        assertThat(saved.get(0).getChunkCount()).isEqualTo(2);
        assertThat(hr.resolve(".documind-files.json")).doesNotExist();
        assertThat(hr.resolve(".documind-files.json.migrated")).exists();
    }
}
