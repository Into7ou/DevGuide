-- V6: 技术栈名大小写不敏感唯一索引（配合 upsertDiscovered 的 ON CONFLICT (LOWER(name))）
-- 防止 "Next.js" 与 "next.js" 并发首次查询时插入两条重复记录。
CREATE UNIQUE INDEX IF NOT EXISTS uq_tech_stacks_name_ci ON tech_stacks (LOWER(name));
