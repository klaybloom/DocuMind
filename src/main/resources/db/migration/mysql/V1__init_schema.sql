CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    role VARCHAR(20) NOT NULL,
    knowledge_bases VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_base VARCHAR(60) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(255),
    owner VARCHAR(80),
    uploaded_by VARCHAR(100),
    uploaded_at VARCHAR(255),
    last_indexed_at VARCHAR(255),
    index_status VARCHAR(16),
    chunk_count INT NOT NULL,
    error VARCHAR(500),
    file_hash VARCHAR(64),
    PRIMARY KEY (id),
    CONSTRAINT uk_document_files_kb_file UNIQUE (knowledge_base, file_name),
    INDEX idx_document_files_knowledge_base (knowledge_base)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_gaps (
    id VARCHAR(255) NOT NULL,
    knowledge_base VARCHAR(255) NOT NULL,
    question VARCHAR(2000) NOT NULL,
    session_id VARCHAR(255),
    created_at VARCHAR(255),
    occurrences INT NOT NULL,
    last_asked_at VARCHAR(255),
    PRIMARY KEY (id),
    INDEX idx_knowledge_gaps_kb_last_asked (knowledge_base, last_asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_events (
    id VARCHAR(255) NOT NULL,
    timestamp VARCHAR(255) NOT NULL,
    actor VARCHAR(255),
    action VARCHAR(60) NOT NULL,
    knowledge_base VARCHAR(255),
    file_name VARCHAR(255),
    details TEXT,
    PRIMARY KEY (id),
    INDEX idx_audit_events_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_bases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_base_owners (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_base VARCHAR(60) NOT NULL,
    username VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_owner UNIQUE (knowledge_base, username),
    INDEX idx_kb_owners_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(100) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_conversations (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(160) NOT NULL,
    knowledge_base VARCHAR(60) NOT NULL,
    title VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_conversation_session UNIQUE (user_id, session_id, knowledge_base),
    CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    INDEX idx_chat_conversations_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id CHAR(36) NOT NULL,
    message_role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE,
    INDEX idx_chat_messages_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation_summaries (
    conversation_id CHAR(36) NOT NULL,
    summary LONGTEXT NOT NULL,
    message_count INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_conversation_summaries_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_memories (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    knowledge_base VARCHAR(60) NOT NULL,
    content TEXT NOT NULL,
    memory_type VARCHAR(40) NOT NULL,
    source_conversation_id CHAR(36),
    auto_generated BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    embedding_json LONGTEXT,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_memories_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_memories_conversation FOREIGN KEY (source_conversation_id) REFERENCES chat_conversations (id) ON DELETE SET NULL,
    INDEX idx_user_memories_lookup (user_id, knowledge_base, enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE memory_extraction_jobs (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_memory_jobs_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE,
    INDEX idx_memory_jobs_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO roles (code, name, system_role) VALUES
    ('SYSTEM_ADMIN', '系统管理员', TRUE),
    ('USER_ADMIN', '用户管理员', TRUE),
    ('KNOWLEDGE_BASE_ADMIN', '知识库管理员', TRUE),
    ('USER', '普通用户', TRUE);

INSERT INTO permissions (code, name) VALUES
    ('SYSTEM_MANAGE', '系统管理'),
    ('USER_MANAGE', '用户管理'),
    ('KNOWLEDGE_BASE_MANAGE', '知识库管理'),
    ('CHAT_USE', '使用问答'),
    ('MEMORY_MANAGE_SELF', '管理本人记忆');
