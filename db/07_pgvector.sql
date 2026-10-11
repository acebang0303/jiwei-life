-- ==========================================
-- 吉味生活 · JiweiLife
-- pgvector 向量存储 (AI 服务专用)
-- 请在 PostgreSQL 中以 superuser 权限执行
-- ==========================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 菜品向量表（与 MySQL dish 表对应，用于语义搜索）
CREATE TABLE IF NOT EXISTS dish_embedding (
    id BIGINT PRIMARY KEY,             -- 对应 MySQL dish.id
    dish_name VARCHAR(64) NOT NULL,
    merchant_id BIGINT NOT NULL,
    category_name VARCHAR(32),
    price DECIMAL(10,2),
    embedding VECTOR(1024) NOT NULL    -- BGE-M3 Embedding 1024 维
);

-- 笔记向量表（用于笔记语义搜索）
CREATE TABLE IF NOT EXISTS note_embedding (
    id BIGINT PRIMARY KEY,             -- 对应 MySQL note.id
    title VARCHAR(128),
    content_summary VARCHAR(512),      -- 正文摘要用于快速展示
    embedding VECTOR(1024) NOT NULL
);

-- 知识库文档表（RAG 知识库）
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    chunk_index INT NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- HNSW 索引（加速向量检索，无需预训练，适合生产环境）
CREATE INDEX IF NOT EXISTS dish_embedding_hnsw_idx ON dish_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS note_embedding_hnsw_idx ON note_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS knowledge_document_hnsw_idx ON knowledge_document USING hnsw (embedding vector_cosine_ops);
