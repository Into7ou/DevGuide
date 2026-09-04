-- V4: user_tokens 生命周期加固（单行模型 + 过期时间）
-- 1) 先清理历史重复行（保留每个 user_id 最新的一条，避免唯一索引创建失败）
DELETE FROM user_tokens a
USING user_tokens b
WHERE a.user_id = b.user_id AND a.id < b.id;

-- 2) 加过期时间列（GitHub classic token 通常为 null 表示不过期；fine-grained 会过期）
ALTER TABLE user_tokens ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- 3) user_id 唯一约束：每个用户只保留一条 token，配合 upsert
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_tokens_user_id ON user_tokens (user_id);
