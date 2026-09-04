-- V2: 记录技术栈是否已入库到向量库（避免每次 learn 重复入库）
ALTER TABLE tech_stacks ADD COLUMN IF NOT EXISTS rag_ingested_at TIMESTAMP;
