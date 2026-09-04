-- V5: tech_stacks 加 category 字段（用于侧边栏分类导航 + 首页筛选）
-- 分类固定为 4 组：frontend / backend / ml-data / infra
ALTER TABLE tech_stacks ADD COLUMN IF NOT EXISTS category VARCHAR(50);

UPDATE tech_stacks SET category = 'frontend' WHERE name IN ('React', 'Vue', 'Angular', 'Svelte', 'Flutter', 'React Native');
UPDATE tech_stacks SET category = 'backend' WHERE name IN ('Spring Boot', 'Django', 'Flask', 'Express', 'NestJS', 'Gin');
UPDATE tech_stacks SET category = 'ml-data' WHERE name IN ('PyTorch', 'TensorFlow', 'LangChain', 'Pandas');
UPDATE tech_stacks SET category = 'infra' WHERE name IN ('Redis', 'Kafka', 'Docker', 'Kubernetes', 'Nginx');
